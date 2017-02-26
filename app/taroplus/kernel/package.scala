package taroplus

import java.util.UUID

import play.api.libs.json.{JsValue, Json}

package object kernel {
  /**
    * Create a message which can be sent back to the client
    * @param orgMsg JsValue the original message
    * @param msgType String, the type of this message
    * @param channel String, channel name
    * @param content JsValue, the content to return
    * @return JsValue the response
    */
  def reply_message(orgMsg: JsValue,
    msgType: String,
    channel: String,
    content: JsValue): JsValue = {

    val msg_id = UUID.randomUUID().toString
    Json.obj("parent_header" -> (orgMsg \ "header").as[JsValue],
      "msg_type" -> msgType,
      "msg_id" -> msg_id,
      "content" -> content,
      "header" -> Json.obj(
        "version" -> "5.0",
        "msg_type" -> msgType,
        "msg_id" -> msg_id
      ),
      "channel" -> channel
    )
  }
}
