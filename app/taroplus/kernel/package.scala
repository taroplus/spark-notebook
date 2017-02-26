package taroplus

import java.util.UUID

import play.api.libs.json.{JsValue, Json}

package object kernel {
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
