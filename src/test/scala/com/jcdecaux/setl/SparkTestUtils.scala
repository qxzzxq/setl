package com.jcdecaux.setl

import com.jcdecaux.setl.util.SparkUtils
import org.apache.spark.SparkContext

private[setl] object SparkTestUtils {

  def getActiveSparkContext: Option[SparkContext] = {
    val method = SparkContext.getClass.getDeclaredMethod("getActive")
    method.setAccessible(true)
    method.invoke(SparkContext).asInstanceOf[Option[SparkContext]]
  }

  def checkSparkVersion(requiredVersion: String): Boolean = SparkUtils.checkSparkVersion(requiredVersion)

}
