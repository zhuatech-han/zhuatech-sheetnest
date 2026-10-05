// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import jakarta.persistence.*;

/** 矩形零件尺寸、需求数量和显式旋转许可，不关联库存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "nest_part")
public class NestPart {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "width_ticks", nullable = false)
  public int widthTicks;

  @Column(name = "height_ticks", nullable = false)
  public int heightTicks;

  @Column(name = "quantity", nullable = false)
  public int quantity;

  @Column(name = "rotation", nullable = false)
  public boolean rotation;
}
