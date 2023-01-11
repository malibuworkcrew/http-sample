package com.interview

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BasicSpec extends AnyWordSpec with Matchers {
  "Basic Object" should {
    "do what we'd expect" in {
      true mustEqual true
    }
  }
}
