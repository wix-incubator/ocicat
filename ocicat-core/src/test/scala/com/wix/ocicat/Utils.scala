package com.wix.ocicat

object Utils {

  def randomString(length: Int): String = {
    val r = new scala.util.Random
    val sb = new StringBuilder
    for (i <- 1 to length) {
      sb.append(r.nextPrintableChar)
    }
    sb.toString
  }
}
