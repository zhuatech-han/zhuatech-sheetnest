// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import jakarta.persistence.*;
import java.time.Instant;

/** 人工登记的合格、报废、未切数量，复核前可修订，冻结后不可改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cut_result")
public class CutResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "part_id", nullable = false)
  public Long partId;

  @Column(name = "good", nullable = false)
  public int good;

  @Column(name = "scrap", nullable = false)
  public int scrap;

  @Column(name = "not_cut", nullable = false)
  public int notCut;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;
}
