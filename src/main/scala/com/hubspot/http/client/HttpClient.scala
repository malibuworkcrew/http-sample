package com.hubspot.http.client
import upickle.default._

case class HttpException(msg: String, ex: Option[Throwable] = None) extends Throwable
// TODO Change these two depending on structure in problem
case class RespStruct(name: String)
case class SendStruct(diffName: String)

class HttpClient(host: String, getPath: String, postPath: String) {
  // Implicit serde formatters for our input/output case classes
  implicit val gw: ReadWriter[RespStruct] = macroRW
  implicit val pw: ReadWriter[SendStruct] = macroRW

  def processGetAndSendPost(): Either[HttpException, String] = try {
    // Simple GET to <host>/<path>
    val r = requests.get(s"$host/$getPath")
    // Return error if request failed
    if (r.statusCode != 200)
      Left(HttpException(s"Got failure status [${r.statusCode}] with " +
        s"message [${new String(r.data.array)}]"))
    else {
      // TODO Change RespStruct to match what we expect
      // Read in response and transform
      val toSend = write(transform(read[RespStruct](r.text)))

      // POST our transformed response
      requests.post(s"$host/$postPath", data = toSend)
      Right(toSend)
    }
  } catch {
    case ex: Throwable =>
      Left(HttpException(s"Unexpected exception during HTTP requests; " +
        s"host = [$host], get = [$getPath], post = [$postPath]", Some(ex)))
  }

  // TODO Do the Xform here for final response
  def transform(resp: RespStruct): SendStruct = {
    val changeName = resp.name + "-changed"
    SendStruct(changeName)
  }
}
