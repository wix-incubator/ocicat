package com.wix.ocicat.storage

import org.scalatest.{FlatSpec, Matchers}

class DoobieStorageTest extends FlatSpec with Matchers {

  "Storage" should "have one dummy test" in new ctx {
    1 shouldBe 1
  }

  trait ctx  {

  }
}
