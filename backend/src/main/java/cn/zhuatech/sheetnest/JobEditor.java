// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import jakarta.persistence.*;

/** 所有曾编辑方案人员都不得独立审签。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "job_editor")
public class JobEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;
}
