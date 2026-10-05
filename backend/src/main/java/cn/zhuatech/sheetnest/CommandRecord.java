// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import jakarta.persistence.*;

/** command_record 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "command_record")
public class CommandRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "request_key", nullable = false, length = 36)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;

  @Column(name = "response_json", nullable = false, columnDefinition = "longtext")
  public String responseJson;
}
