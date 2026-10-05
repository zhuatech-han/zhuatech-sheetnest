// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import jakarta.persistence.*;
import java.time.Instant;

/** 每次计算的不可变输入和布局，保留历史及算法版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "nest_revision")
public class NestRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "algorithm", nullable = false, length = 80)
  public String algorithm;

  @Column(name = "input_hash", nullable = false, length = 64)
  public String inputHash;

  @Column(name = "plan_hash", nullable = false, length = 64)
  public String planHash;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "input_json", nullable = false, columnDefinition = "longtext")
  public String inputJson;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "plan_json", nullable = false, columnDefinition = "longtext")
  public String planJson;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
