package com.hubspot.http

import com.hubspot.http.client.HttpClient

import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {
    val host = args.headOption.getOrElse("https://hubspotwood.free.beeceptor.com")
    val getPath = Try(args(1)).getOrElse("get")
    val postPath = Try(args(2)).getOrElse("post")

    new HttpClient(host, getPath, postPath).processGetAndSendPost() match {
      case Left(error) =>
        println(s"Error in execution: ${error.msg}")
        error.ex.foreach(_.printStackTrace())
      case Right(sent) =>
        println(s"Successful get/post with response [$sent]")
    }
  }
}
