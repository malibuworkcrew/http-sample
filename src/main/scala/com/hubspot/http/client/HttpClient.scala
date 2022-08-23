package com.hubspot.http.client
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import upickle.default._

import java.text.SimpleDateFormat
import scala.collection.mutable

case class HttpException(msg: String, ex: Option[Throwable] = None) extends Throwable
case class Person(firstName: String, lastName: String, email: String, country: String, availableDates: List[String])
// Count of attendees, list of emails, country name, start date in yyyy-MM-dd
case class Attendees(attendeeCount: Int, attendees: List[String], name: String, startDate: String)
case class RespStruct(partners: List[Person])
case class SendStruct(countries: List[Attendees])

class HttpClient(host: String, getPath: String, postPath: String) {
  // Implicit required serde formatters for our input/output case classes
  implicit val persWR: ReadWriter[Person] = macroRW
  implicit val attendWR: ReadWriter[Attendees] = macroRW
  implicit val respWR: ReadWriter[RespStruct] = macroRW
  implicit val sendWR: ReadWriter[SendStruct] = macroRW
  // Date formatters for availability comparisons
  val timeFormat: SimpleDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd")
  val dtf: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

  def processGetAndSendPost(): Either[HttpException, String] = try {
    // Simple GET to HS servers
    val r = requests.get(s"$host/$getPath")
    // Return error if request failed
    if (r.statusCode != 200)
      Left(HttpException(s"Got failure status [${r.statusCode}] with " +
        s"message [${new String(r.data.array)}]"))
    else {
      // Read in response and transform
      val toSend = transformResponse(read[RespStruct](r.text))

      // POST our transformed response
      requests.post(s"$host/$postPath", headers = Map("Content-Type" -> "application/json"),
        data = write(toSend))
      Right(write(toSend))
    }
  } catch {
    case ex: Throwable =>
      Left(HttpException(s"Unexpected exception during HTTP requests; " +
        s"host = [$host], get = [$getPath], post = [$postPath]", Some(ex)))
  }

  def transformResponse(resp: RespStruct): SendStruct = {
    // Group partners by the country they are from
    val countryMap = resp.partners.groupBy(_.country)

    SendStruct(countryMap.map { case (country, people) =>
      // Map to hold the common dates everyone is available
      val dateMap = mutable.Map[DateTime, List[Person]]()
      people.foreach { person =>
        // Go through this person's available dates
        person.availableDates.foreach { pDate =>
          val date = DateTime.parse(pDate)
          // Add person to map of those available on this date
          dateMap.get(date) match {
            case Some(people) =>
              dateMap.update(date, people :+ person)
            case None =>
              dateMap.update(date, List(person))
          }
        }
      }

      // Check who is available for both the given dates
      def checkDate(first: DateTime, second: DateTime): List[Person] = {
        val firstAvailable = dateMap.getOrElse(first, List())
        val secondAvailable = dateMap.getOrElse(second, List())
        firstAvailable.intersect(secondAvailable)
      }

      // Find the earliest date with the most people who can attend both days
      var bestStart: Option[(DateTime, List[Person])] = None
      dateMap.foreach { case (date, _) =>
        val canMakeIt = checkDate(date, date.plusDays(1))
        val currentSize = bestStart.map(_._2.size).getOrElse(0)
        if (bestStart.isDefined && currentSize == canMakeIt.size && bestStart.get._1.isAfter(date))
          bestStart = Some((date, canMakeIt))
        else if (currentSize < canMakeIt.size)
          bestStart = Some((date, canMakeIt))
      }

      // Grab fields we need for attendee object
      val attendeeCount = bestStart.map(_._2.size).getOrElse(0)
      val emailList = bestStart.map(_._2.map(_.email)).getOrElse(List())
      Attendees(attendeeCount, emailList, country, bestStart.map(d => dtf.print(d._1)).orNull)
    }.toList)
  }
}
