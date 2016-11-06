/*
 * Copyright 2014–2016 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ygg.table

import scala.Predef.$conforms
import ygg.common._
import scalaz._, Scalaz._
import ygg.data._

class TableIndex(private[table] val indices: List[SliceIndex]) {

  /**
    * Return the set of values we've seen for this group key.
    */
  def getUniqueKeys(keyId: Int): Set[RValue] =
    // Union the sets we get from our slice indices.
    indices flatMap (_ getUniqueKeys keyId) toSet

  /**
    * Return the set of values we've seen for this group key.
    */
  def getUniqueKeys(): Set[Seq[RValue]] =
    // Union the sets we get from our slice indices.
    indices flatMap (_.getUniqueKeys()) toSet
}

object TableIndex {
  /**
    * Create an empty TableIndex.
    */
  def empty = new TableIndex(Nil)

  /**
    * Creates a TableIndex instance given an underlying table, a
    * sequence of "group key" trans-specs, and "value" trans-spec
    * which corresponds to the rows the index will provide.
    *
    * Despite being in M, the TableIndex will be eagerly constructed
    * as soon as the underlying slices are available.
    */
  def createFromTable[T](rep: TableRep[T], groupKeys: Seq[TransSpec1], valueSpec: TransSpec1): Need[TableIndex] = {
    import rep._

    def accumulate(buf: ListBuffer[SliceIndex], stream: StreamT[Need, SliceIndex]): Need[TableIndex] =
      stream.uncons flatMap {
        case None             => Need(new TableIndex(buf.toList))
        case Some((si, tail)) => { buf += si; accumulate(buf, tail) }
      }

    // We are given TransSpec1s; to apply these to slices we need to
    // create SliceTransforms from them.
    val sts = groupKeys.map(composeSliceTransform).toArray
    val vt  = composeSliceTransform(valueSpec)

    val indices = table.slices flatMap { slice =>
      val streamTM = SliceIndex.createFromSlice(slice, sts, vt) map (si => singleStreamT(si))

      StreamT wrapEffect streamTM
    }

    accumulate(ListBuffer.empty[SliceIndex], indices)
  }

  /**
    * For a list of slice indices (along with projection
    * information), return a table containing all the rows for which
    * any of the given indices match.
    *
    * NOTE: Only the first index's value spec is used to construct
    * the table, since it's assumed that all indices have the same
    * value spec.
    */
  def joinSubTables[T](rep: TableRep[T], tpls: List[(TableIndex, Seq[Int], Seq[RValue])]): T = {
    import rep._

    // Filter out negative integers. This allows the caller to do
    // arbitrary remapping of their own Seq[RValue] by filtering
    // values they don't want.
    val params: List[Seq[Int] -> Seq[RValue]] = tpls.map {
      case (index, ns, jvs) =>
        val (ns2, jvs2) = ns.zip(jvs).filter(_._1 >= 0).unzip
        (ns2, jvs2)
    }

    val sll: List[List[SliceIndex]]            = tpls.map(_._1.indices)
    val orderedIndices: List[List[SliceIndex]] = sll.transpose

    var size = 0L
    val slices: List[Slice] = orderedIndices.map { indices =>
      val slice = companion.joinSubSlices(indices.zip(params))
      size += slice.size
      slice
    }

    companion(StreamT.fromStream(Need(slices.toStream)), ExactSize(size))
  }
}

/**
  * Provide fast access to a subslice based on one or more group key
  * values.
  *
  * The SliceIndex currently uses in-memory data structures, although
  * this will have to change eventually. A "group key value" is
  * defined as an (Int, RValue). The Int part corresponds to the key
  * in the sequence of transforms used to build the index, and the
  * RValue part corresponds to the value we want the key to have.
  *
  * SliceIndex is able to create subslices without rescanning the
  * underlying slice due to the fact that it already knows which rows
  * are valid for particular key combinations. For best results
  * valueSlice should already be materialized.
  */
class SliceIndex(
    private[table] val vals: scmMap[Int, scmSet[RValue]],
    private[table] val dict: scmMap[(Int, RValue), ArrayIntList],
    private[table] val keyset: scmSet[Seq[RValue]],
    private[table] val valueSlice: Slice
) {

  // TODO: We're currently maintaining a *lot* of indices. Once we
  // find the patterns of use, it'd be nice to reduce the amount of
  // data we're indexing if possible.

  /**
    * Return the set of values we've seen for this group key.
    */
  def getUniqueKeys(keyId: Int): Set[RValue] = vals(keyId).toSet

  /**
    * Return the set of value combinations we've seen.
    */
  def getUniqueKeys(): Set[Seq[RValue]] = keyset.toSet
}

object SliceIndex {

  /**
    * Constructs an empty SliceIndex instance.
    */
  def empty = new SliceIndex(
    scmMap[Int, scmSet[RValue]](),
    scmMap[(Int, RValue), ArrayIntList](),
    scmSet[Seq[RValue]](),
    Slice.empty
  )

  /**
    * Creates a SliceIndex instance given an underlying table, a
    * sequence of "group key" trans-specs, and "value" trans-spec
    * which corresponds to the rows the index will provide.
    *
    * Despite being in M, the SliceIndex will be eagerly constructed
    * as soon as the underlying Slice is available.
    */
  def createFromTable[T](rep: TableRep[T], groupKeys: Seq[TransSpec1], valueSpec: TransSpec1): Need[SliceIndex] = {
    import rep._
    val sts = groupKeys.map(composeSliceTransform).toArray
    val vt  = composeSliceTransform(valueSpec)

    table.slices.uncons flatMap {
      case Some((slice, _)) => createFromSlice(slice, sts, vt)
      case None             => Need(SliceIndex.empty)
    }
  }

  /**
    * Given a slice, group key transforms, and a value transform,
    * builds a SliceIndex.
    *
    * This is the heart of the indexing algorithm. We'll assemble a
    * 2D array of RValue (by row/group key) and then do all the work
    * necessary to associate them into the maps and sets we
    * ultimately need to construct the SliceIndex.
    */
  private[table] def createFromSlice(slice: Slice, sts: Array[SliceTransform1[_]], vt: SliceTransform1[_]): Need[SliceIndex] = {
    val numKeys = sts.length
    val n       = slice.size
    val vals    = scmMap[Int, scmSet[RValue]]()
    val dict    = scmMap[(Int, RValue), ArrayIntList]()
    val keyset  = scmSet[Seq[RValue]]()

    readKeys(slice, sts) flatMap { keys =>
      // build empty initial jvalue sets for our group keys
      Loop.range(0, numKeys)(vals(_) = scmSet[RValue]())

      var i = 0
      while (i < n) {
        var dead = false
        val row  = new Array[RValue](numKeys)
        var k    = 0
        while (!dead && k < numKeys) {
          val jv = keys(k)(i)
          if (jv != null) {
            row(k) = jv
          } else {
            dead = true
          }
          k += 1
        }

        if (!dead) {
          keyset.add(row.toVector)
          k = 0
          while (k < numKeys) {
            val jv = row(k)
            vals.get(k).map { jvs =>
              jvs.add(jv)
              val key = (k, jv)
              if (dict.contains(key)) {
                dict(key).add(i)
              } else {
                val as = new ArrayIntList(0)
                as.add(i)
                dict(key) = as
              }
            }
            k += 1
          }
        }
        i += 1
      }

      vt(slice) map {
        case (_, slice2) =>
          new SliceIndex(vals, dict, keyset, slice2.materialized)
      }
    }
  }

  /**
    * Given a slice and an array of group key transforms, we want to
    * build a two-dimensional array which contains the values
    * per-row, per-column. This is how we deal with the fact that our
    * data store is column-oriented but the associations we want to
    * perform are row-oriented.
    */
  private[table] def readKeys(slice: Slice, sts: Array[SliceTransform1[_]]): Need[Array[Array[RValue]]] = {
    val n       = slice.size
    val numKeys = sts.length
    val keys    = new ArrayBuffer[Need[Array[RValue]]](numKeys)

    (0 until numKeys) foreach { _ =>
      keys += null.asInstanceOf[Need[Array[RValue]]]
    }

    var k = 0
    while (k < numKeys) {
      val st: SliceTransform1[_] = sts(k)

      keys(k) = st(slice) map {
        case (_, keySlice) => {
          val arr = new Array[RValue](n)

          var i = 0
          while (i < n) {
            val rv = keySlice.toRValue(i)
            rv match {
              case CUndefined =>
              case rv         => arr(i) = rv
            }
            i += 1
          }

          arr
        }
      }

      k += 1
    }

    val back = (0 until keys.length).foldLeft(Need(Vector.fill[Array[RValue]](numKeys)(null))) {
      case (accM, i) => {
        val arrM = keys(i)

        Need.need.apply2(accM, arrM) { (acc, arr) =>
          acc.updated(i, arr)
        }
      }
    }

    back map { _.toArray }
  }
}

