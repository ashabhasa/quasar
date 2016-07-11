package blueeyes.core.storeable

import blueeyes.json.JValue
import org.joda.time.DateTime

trait ValueImplicits {
  implicit def intToValue(value: Int)               = ValueInt(value)
  implicit def bigIntToValue(value: BigInt)         = ValueBigInt(value)
  implicit def bigDecimalToValue(value: BigDecimal) = ValueBigDecimal(value)
  implicit def booleanToValue(value: Boolean)       = ValueBoolean(value)
  implicit def doubleToValue(value: Double)         = ValueDouble(value)
  implicit def stringToValue(value: String)         = ValueString(value)
  implicit def charToValue(value: Char)             = ValueChar(value)
  implicit def byteToValue(value: Byte)             = ValueByte(value)
  implicit def floatToValue(value: Float)           = ValueFloat(value)
  implicit def longToValue(value: Long)             = ValueLong(value)
  implicit def shortToValue(value: Short)           = ValueShort(value)
  implicit def dateTimeToValue(value: DateTime)     = ValueDateTime(value)

  implicit def jsonToValue(value: JValue)                                                                                    = ValueJson(value)
  implicit def optionToValue[T](value: Option[T])(implicit valueToStoreable: T => Storeable)                                 = ValueOption(value)
  implicit def seqToValue[T](value: Seq[T])(implicit valueToStoreable: T => Storeable)                                       = ValueSeq(value)
  implicit def setToValue[T](value: Set[T])(implicit valueToStoreable: T => Storeable)                                       = ValueSet(value)
  implicit def listToValue[T](value: List[T])(implicit valueToStoreable: T => Storeable)                                     = ValueList(value)
  implicit def arrayToValue[T](value: Array[T])(implicit valueToStoreable: T => Storeable)                                   = ValueArray(value)
  implicit def mapToValue[K, V](value: Map[K, V])(implicit keyToStoreable: K => Storeable, valueToStoreable: V => Storeable) = ValueMap(value)

  implicit def tuple1ToValue[T1](value: Tuple1[T1])(implicit vToS: T1 => Storeable)                                  = ValueTuple1(value)
  implicit def tuple2ToValue[T1, T2](value: Tuple2[T1, T2])(implicit v1ToS: T1 => Storeable, v2ToS: T2 => Storeable) = ValueTuple2(value)
  implicit def tuple3ToValue[T1, T2, T3](value: Tuple3[T1, T2, T3])(implicit v1ToS: T1 => Storeable, v2ToS: T2 => Storeable, v3ToS: T3 => Storeable) =
    ValueTuple3(value)
  implicit def tuple4ToValue[T1, T2, T3, T4](
      value: Tuple4[T1, T2, T3, T4])(implicit v1ToS: T1 => Storeable, v2ToS: T2 => Storeable, v3ToS: T3 => Storeable, v4ToS: T4 => Storeable) =
    ValueTuple4(value)
  implicit def tuple5ToValue[T1, T2, T3, T4, T5](value: Tuple5[T1, T2, T3, T4, T5])(implicit v1ToS: T1 => Storeable,
                                                                                    v2ToS: T2 => Storeable,
                                                                                    v3ToS: T3 => Storeable,
                                                                                    v4ToS: T4 => Storeable,
                                                                                    v5ToS: T5 => Storeable) = ValueTuple5(value)
  implicit def tuple6ToValue[T1, T2, T3, T4, T5, T6](value: Tuple6[T1, T2, T3, T4, T5, T6])(implicit v1ToS: T1 => Storeable,
                                                                                            v2ToS: T2 => Storeable,
                                                                                            v3ToS: T3 => Storeable,
                                                                                            v4ToS: T4 => Storeable,
                                                                                            v5ToS: T5 => Storeable,
                                                                                            v6ToS: T6 => Storeable) = ValueTuple6(value)
  implicit def tuple7ToValue[T1, T2, T3, T4, T5, T6, T7](value: Tuple7[T1, T2, T3, T4, T5, T6, T7])(implicit v1ToS: T1 => Storeable,
                                                                                                    v2ToS: T2 => Storeable,
                                                                                                    v3ToS: T3 => Storeable,
                                                                                                    v4ToS: T4 => Storeable,
                                                                                                    v5ToS: T5 => Storeable,
                                                                                                    v6ToS: T6 => Storeable,
                                                                                                    v7ToS: T7 => Storeable) = ValueTuple7(value)
  implicit def tuple8ToValue[T1, T2, T3, T4, T5, T6, T7, T8](value: Tuple8[T1, T2, T3, T4, T5, T6, T7, T8])(implicit v1ToS: T1 => Storeable,
                                                                                                            v2ToS: T2 => Storeable,
                                                                                                            v3ToS: T3 => Storeable,
                                                                                                            v4ToS: T4 => Storeable,
                                                                                                            v5ToS: T5 => Storeable,
                                                                                                            v6ToS: T6 => Storeable,
                                                                                                            v7ToS: T7 => Storeable,
                                                                                                            v8ToS: T8 => Storeable) = ValueTuple8(value)
  implicit def tuple9ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9](value: Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9])(implicit v1ToS: T1 => Storeable,
                                                                                                                    v2ToS: T2 => Storeable,
                                                                                                                    v3ToS: T3 => Storeable,
                                                                                                                    v4ToS: T4 => Storeable,
                                                                                                                    v5ToS: T5 => Storeable,
                                                                                                                    v6ToS: T6 => Storeable,
                                                                                                                    v7ToS: T7 => Storeable,
                                                                                                                    v8ToS: T8 => Storeable,
                                                                                                                    v9ToS: T9 => Storeable) =
    ValueTuple9(value)
  implicit def tuple10ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](value: Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10])(
      implicit v1ToS: T1 => Storeable,
      v2ToS: T2 => Storeable,
      v3ToS: T3 => Storeable,
      v4ToS: T4 => Storeable,
      v5ToS: T5 => Storeable,
      v6ToS: T6 => Storeable,
      v7ToS: T7 => Storeable,
      v8ToS: T8 => Storeable,
      v9ToS: T9 => Storeable,
      v10ToS: T10 => Storeable) = ValueTuple10(value)
  implicit def tuple11ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](value: Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11])(
      implicit v1ToS: T1 => Storeable,
      v2ToS: T2 => Storeable,
      v3ToS: T3 => Storeable,
      v4ToS: T4 => Storeable,
      v5ToS: T5 => Storeable,
      v6ToS: T6 => Storeable,
      v7ToS: T7 => Storeable,
      v8ToS: T8 => Storeable,
      v9ToS: T9 => Storeable,
      v10ToS: T10 => Storeable,
      v11ToS: T11 => Storeable) = ValueTuple11(value)
  implicit def tuple12ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](value: Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12])(
      implicit v1ToS: T1 => Storeable,
      v2ToS: T2 => Storeable,
      v3ToS: T3 => Storeable,
      v4ToS: T4 => Storeable,
      v5ToS: T5 => Storeable,
      v6ToS: T6 => Storeable,
      v7ToS: T7 => Storeable,
      v8ToS: T8 => Storeable,
      v9ToS: T9 => Storeable,
      v10ToS: T10 => Storeable,
      v11ToS: T11 => Storeable,
      v12ToS: T12 => Storeable) = ValueTuple12(value)
  implicit def tuple13ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](value: Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13])(
      implicit v1ToS: T1 => Storeable,
      v2ToS: T2 => Storeable,
      v3ToS: T3 => Storeable,
      v4ToS: T4 => Storeable,
      v5ToS: T5 => Storeable,
      v6ToS: T6 => Storeable,
      v7ToS: T7 => Storeable,
      v8ToS: T8 => Storeable,
      v9ToS: T9 => Storeable,
      v10ToS: T10 => Storeable,
      v11ToS: T11 => Storeable,
      v12ToS: T12 => Storeable,
      v13ToS: T13 => Storeable) = ValueTuple13(value)
  implicit def tuple14ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](
      value: Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14])(implicit v1ToS: T1 => Storeable,
                                                                                   v2ToS: T2 => Storeable,
                                                                                   v3ToS: T3 => Storeable,
                                                                                   v4ToS: T4 => Storeable,
                                                                                   v5ToS: T5 => Storeable,
                                                                                   v6ToS: T6 => Storeable,
                                                                                   v7ToS: T7 => Storeable,
                                                                                   v8ToS: T8 => Storeable,
                                                                                   v9ToS: T9 => Storeable,
                                                                                   v10ToS: T10 => Storeable,
                                                                                   v11ToS: T11 => Storeable,
                                                                                   v12ToS: T12 => Storeable,
                                                                                   v13ToS: T13 => Storeable,
                                                                                   v14ToS: T14 => Storeable) = ValueTuple14(value)
  implicit def tuple15ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](
      value: Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15])(implicit v1ToS: T1 => Storeable,
                                                                                        v2ToS: T2 => Storeable,
                                                                                        v3ToS: T3 => Storeable,
                                                                                        v4ToS: T4 => Storeable,
                                                                                        v5ToS: T5 => Storeable,
                                                                                        v6ToS: T6 => Storeable,
                                                                                        v7ToS: T7 => Storeable,
                                                                                        v8ToS: T8 => Storeable,
                                                                                        v9ToS: T9 => Storeable,
                                                                                        v10ToS: T10 => Storeable,
                                                                                        v11ToS: T11 => Storeable,
                                                                                        v12ToS: T12 => Storeable,
                                                                                        v13ToS: T13 => Storeable,
                                                                                        v14ToS: T14 => Storeable,
                                                                                        v15ToS: T15 => Storeable) = ValueTuple15(value)
  implicit def tuple16ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](
      value: Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16])(implicit v1ToS: T1 => Storeable,
                                                                                             v2ToS: T2 => Storeable,
                                                                                             v3ToS: T3 => Storeable,
                                                                                             v4ToS: T4 => Storeable,
                                                                                             v5ToS: T5 => Storeable,
                                                                                             v6ToS: T6 => Storeable,
                                                                                             v7ToS: T7 => Storeable,
                                                                                             v8ToS: T8 => Storeable,
                                                                                             v9ToS: T9 => Storeable,
                                                                                             v10ToS: T10 => Storeable,
                                                                                             v11ToS: T11 => Storeable,
                                                                                             v12ToS: T12 => Storeable,
                                                                                             v13ToS: T13 => Storeable,
                                                                                             v14ToS: T14 => Storeable,
                                                                                             v15ToS: T15 => Storeable,
                                                                                             v16ToS: T16 => Storeable) = ValueTuple16(value)
  implicit def tuple17ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](
      value: Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17])(implicit v1ToS: T1 => Storeable,
                                                                                                  v2ToS: T2 => Storeable,
                                                                                                  v3ToS: T3 => Storeable,
                                                                                                  v4ToS: T4 => Storeable,
                                                                                                  v5ToS: T5 => Storeable,
                                                                                                  v6ToS: T6 => Storeable,
                                                                                                  v7ToS: T7 => Storeable,
                                                                                                  v8ToS: T8 => Storeable,
                                                                                                  v9ToS: T9 => Storeable,
                                                                                                  v10ToS: T10 => Storeable,
                                                                                                  v11ToS: T11 => Storeable,
                                                                                                  v12ToS: T12 => Storeable,
                                                                                                  v13ToS: T13 => Storeable,
                                                                                                  v14ToS: T14 => Storeable,
                                                                                                  v15ToS: T15 => Storeable,
                                                                                                  v16ToS: T16 => Storeable,
                                                                                                  v17ToS: T17 => Storeable) = ValueTuple17(value)
  implicit def tuple18ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](
      value: Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18])(implicit v1ToS: T1 => Storeable,
                                                                                                       v2ToS: T2 => Storeable,
                                                                                                       v3ToS: T3 => Storeable,
                                                                                                       v4ToS: T4 => Storeable,
                                                                                                       v5ToS: T5 => Storeable,
                                                                                                       v6ToS: T6 => Storeable,
                                                                                                       v7ToS: T7 => Storeable,
                                                                                                       v8ToS: T8 => Storeable,
                                                                                                       v9ToS: T9 => Storeable,
                                                                                                       v10ToS: T10 => Storeable,
                                                                                                       v11ToS: T11 => Storeable,
                                                                                                       v12ToS: T12 => Storeable,
                                                                                                       v13ToS: T13 => Storeable,
                                                                                                       v14ToS: T14 => Storeable,
                                                                                                       v15ToS: T15 => Storeable,
                                                                                                       v16ToS: T16 => Storeable,
                                                                                                       v17ToS: T17 => Storeable,
                                                                                                       v18ToS: T18 => Storeable) = ValueTuple18(value)
  implicit def tuple19ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](
      value: Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19])(implicit v1ToS: T1 => Storeable,
                                                                                                            v2ToS: T2 => Storeable,
                                                                                                            v3ToS: T3 => Storeable,
                                                                                                            v4ToS: T4 => Storeable,
                                                                                                            v5ToS: T5 => Storeable,
                                                                                                            v6ToS: T6 => Storeable,
                                                                                                            v7ToS: T7 => Storeable,
                                                                                                            v8ToS: T8 => Storeable,
                                                                                                            v9ToS: T9 => Storeable,
                                                                                                            v10ToS: T10 => Storeable,
                                                                                                            v11ToS: T11 => Storeable,
                                                                                                            v12ToS: T12 => Storeable,
                                                                                                            v13ToS: T13 => Storeable,
                                                                                                            v14ToS: T14 => Storeable,
                                                                                                            v15ToS: T15 => Storeable,
                                                                                                            v16ToS: T16 => Storeable,
                                                                                                            v17ToS: T17 => Storeable,
                                                                                                            v18ToS: T18 => Storeable,
                                                                                                            v19ToS: T19 => Storeable) = ValueTuple19(value)
  implicit def tuple20ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](
      value: Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20])(implicit v1ToS: T1 => Storeable,
                                                                                                                 v2ToS: T2 => Storeable,
                                                                                                                 v3ToS: T3 => Storeable,
                                                                                                                 v4ToS: T4 => Storeable,
                                                                                                                 v5ToS: T5 => Storeable,
                                                                                                                 v6ToS: T6 => Storeable,
                                                                                                                 v7ToS: T7 => Storeable,
                                                                                                                 v8ToS: T8 => Storeable,
                                                                                                                 v9ToS: T9 => Storeable,
                                                                                                                 v10ToS: T10 => Storeable,
                                                                                                                 v11ToS: T11 => Storeable,
                                                                                                                 v12ToS: T12 => Storeable,
                                                                                                                 v13ToS: T13 => Storeable,
                                                                                                                 v14ToS: T14 => Storeable,
                                                                                                                 v15ToS: T15 => Storeable,
                                                                                                                 v16ToS: T16 => Storeable,
                                                                                                                 v17ToS: T17 => Storeable,
                                                                                                                 v18ToS: T18 => Storeable,
                                                                                                                 v19ToS: T19 => Storeable,
                                                                                                                 v20ToS: T20 => Storeable) =
    ValueTuple20(value)
  implicit def tuple21ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](
      value: Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21])(implicit v1ToS: T1 => Storeable,
                                                                                                                      v2ToS: T2 => Storeable,
                                                                                                                      v3ToS: T3 => Storeable,
                                                                                                                      v4ToS: T4 => Storeable,
                                                                                                                      v5ToS: T5 => Storeable,
                                                                                                                      v6ToS: T6 => Storeable,
                                                                                                                      v7ToS: T7 => Storeable,
                                                                                                                      v8ToS: T8 => Storeable,
                                                                                                                      v9ToS: T9 => Storeable,
                                                                                                                      v10ToS: T10 => Storeable,
                                                                                                                      v11ToS: T11 => Storeable,
                                                                                                                      v12ToS: T12 => Storeable,
                                                                                                                      v13ToS: T13 => Storeable,
                                                                                                                      v14ToS: T14 => Storeable,
                                                                                                                      v15ToS: T15 => Storeable,
                                                                                                                      v16ToS: T16 => Storeable,
                                                                                                                      v17ToS: T17 => Storeable,
                                                                                                                      v18ToS: T18 => Storeable,
                                                                                                                      v19ToS: T19 => Storeable,
                                                                                                                      v20ToS: T20 => Storeable,
                                                                                                                      v21ToS: T21 => Storeable) =
    ValueTuple21(value)
  implicit def tuple22ToValue[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](
      value: Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22])(implicit v1ToS: T1 => Storeable,
                                                                                                                           v2ToS: T2 => Storeable,
                                                                                                                           v3ToS: T3 => Storeable,
                                                                                                                           v4ToS: T4 => Storeable,
                                                                                                                           v5ToS: T5 => Storeable,
                                                                                                                           v6ToS: T6 => Storeable,
                                                                                                                           v7ToS: T7 => Storeable,
                                                                                                                           v8ToS: T8 => Storeable,
                                                                                                                           v9ToS: T9 => Storeable,
                                                                                                                           v10ToS: T10 => Storeable,
                                                                                                                           v11ToS: T11 => Storeable,
                                                                                                                           v12ToS: T12 => Storeable,
                                                                                                                           v13ToS: T13 => Storeable,
                                                                                                                           v14ToS: T14 => Storeable,
                                                                                                                           v15ToS: T15 => Storeable,
                                                                                                                           v16ToS: T16 => Storeable,
                                                                                                                           v17ToS: T17 => Storeable,
                                                                                                                           v18ToS: T18 => Storeable,
                                                                                                                           v19ToS: T19 => Storeable,
                                                                                                                           v20ToS: T20 => Storeable,
                                                                                                                           v21ToS: T21 => Storeable,
                                                                                                                           v22ToS: T22 => Storeable) =
    ValueTuple22(value)
}

object ValueImplicits extends ValueImplicits
