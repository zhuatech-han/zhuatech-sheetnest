[中文](README.md) | [English](README.en.md)

# SheetNest · 知华板材排样与切割结果复核系统

![知华科技 LOGO](frontend/public/brand/logo.jpg)

**知华科技（上海如静知华信息科技有限公司）** · <https://www.zhuatech.cn/> · 商业授权、定制开发、部署与系统集成咨询微信 **zhuatech / zhuatech2**。

**公开源码学习版／非商业源码版**，版本0.1.0。自有源码适用 [ZhuaTech Non-Commercial Source License 1.0](LICENSE)，未经书面授权不得商用。公开可读源码不等于 OSI 开源许可；第三方组件保留各自版权与许可。

矩形零件下料前，需要把板材尺寸、方向许可、留边和切缝放在同一张可复核的图中。SheetNest面向木板、金属板、塑料板等单一规格矩形排样的学习与内部流程研究，保存计算输入、历史布局、独立方案批准和人工实物结果，便于复算及追溯。板材采购、库存、工单排程和设备控制不在本版范围。

[操作手册](docs/操作手册.md) · [接口说明](docs/接口说明.md) · [架构与数据](docs/架构与数据.md) · [部署与恢复](docs/部署与恢复.md) · [安全说明](SECURITY.md)

## 一张方案怎样完成

```text
定义板材与零件 → 计算排样 → 完整方案送审 → 独立批准并冻结
       ↑             ↓                               ↓
    输入修订     部分未排入需修订                 开启实物登记
                                                       ↓
                         指定登记人收悉 → 合格／报废／未切人工登记
                                                       ↓
                         提交完成或停止报告 → 独立复核／退回 → 封存
```

“开始登记”开启软件记录流程，不发送任何加工命令。方案编辑人、实物登记人与复核人相互独立；历史编辑人不会因换角色而变成独立复核人。

## 可用能力

| 模块 | 已实现行为 |
|---|---|
| 方案输入 | 唯一编号、负责部门、材料类型与规格说明、板材宽高、四边留边、切缝、最多板数、指定复核／登记岗位 |
| 矩形需求 | 最多30种、合计300件；毫米尺寸最多一位小数；每种零件显式允许或禁止旋转90° |
| 排样核心 | 自主整数贯穿切割启发式，八种排序／分割策略比较；确定性输出完整切割树、零件位置、切缝、余料和未排入数量 |
| 方案复核 | 当前计算与输入摘要校验；仅全部排入方案可送审；指定独立复核人批准／退回；批准输入和布局冻结 |
| 历史版本 | 每次计算保存输入JSON、布局JSON、算法版本及SHA-256；修订使活动结果失效，历史版本仍可读取与复算 |
| 实物登记 | 指定人收悉后人工逐项填写合格、报废、未切数量；异常须说明；未登记显示空白，不能自动当成零 |
| 结果封存 | 送审须逐项数量守恒；明确完成或停止申报；独立复核／退回；全部合格、有报废／未切及停止分别保留结局 |
| 图与统计 | 逐张板材SVG图、逻辑分割顺序、平方米面积账、范围内方案状态和人工实物计数 |
| 查询与导出 | 授权搜索、筛选、排序、分页；范围内JSON完整报告及尺寸／实物CSV，防CSV公式注入 |
| 用户／管理端 | 会话登录、退出与改密；用户、五种角色、部门、菜单、八项权限、材料字典、系统参数、操作审计 |
| 页面 | 中文／英文、电脑／手机布局、真实空状态与反馈、正式知华LOGO及咨询入口 |

本版没有全局最优求解、多板规格组合、异形轮廓、纹理识别、缺陷避让、价格最优、库存联动、生产工单、G-code、装夹／进给参数、机床接口或设备安全认证。没有预置业务案例、仿真加工或第三方服务演示模式；核心流程使用真实数据库。无需AI、外部账号或设备配置。对外HTTPS及备份存储由部署方配置。

## 排样口径与限制

- 算法标记`GUILLOTINE-PORTFOLIO-1`。需求按面积、长边、宽、高四种确定性次序排序，分别比较纵向优先／横向优先分割；候选矩形采用短边剩余、长边剩余及稳定编号排序。八种结果以排入件数优先、使用板数其次、切缝面积再次比较，仍不保证最优。
- 每次分割贯穿当前子板，消费完整切缝宽度。余量为零时不再切割；余量大于零但不足切缝宽时保守拒绝该位置。切缝为零可显式输入，不被默认值替换。
- 尺寸内部是整数0.1mm，面积源单位0.01mm²；图与切割表转换为mm，面积账转换为m²。零件＋切缝＋余料＋留边＝使用整板面积。利用率＝零件面积／使用整板面积；最多允许板数不作为分母。
- 旋转许可是人工输入。固定方向保留原宽高；允许旋转可能交换宽高。材料规格／厚度是说明文字，没有自动物性、纹理、混料或加工可行性判断。
- 一张方案只用同一规格板材；尺寸1—50000mm，留边0—2000mm且小于两边尺寸的一半，切缝0—100mm，板数1—30。最多50次历史计算、100—1000张方案（系统参数）、10000个成功业务请求键。
- 逻辑分割序为树的先序遍历，适合人工核对，不包含留边加工步骤、设备顺序约束或机械安全参数。余料只表示剩余矩形，不自动生成库存、售卖或复用承诺。
- 完成申报有报废／未切时封存为`SHORTFALL`；全部合格为`COMPLETED`；明确停止申报为`STOPPED`。都要求人工完整登记，不推断实际发生的加工。

[RectangleBinPack原始项目](https://github.com/juj/RectangleBinPack)区分精确与近似矩形排样方法，[贯穿切割问题说明](https://fontanf.github.io/packingsolver/rectangleguillotine.html)说明贯穿当前板片的切割模型。本版自主编写有界启发式，没有复制上述项目代码，也不声明相同求解能力。

## 岗位与实时权限

| 默认岗位 | 数据及操作 |
|---|---|
| 管理员 | ALL目录与身份管理，业务动作仍受指定岗位及独立性约束 |
| 方案设计 | 本部门方案、零件、计算、送审、登记开启、取消与导出 |
| 独立复核 | 本部门查阅；只对本人被指定且未编辑／执行的方案批准与封存 |
| 实物登记 | SELF范围，只查看本人指定方案，收悉、登记、提交实物报告 |
| 部门查阅 | 本部门只读与导出，无业务写入 |

账号、部门与权限每次实时检查；业务写入先序列化授权，再检查UUID幂等与整方案版本。撤权、停用及改密不会被缓存成功响应绕过。纯登记岗位即使配置ALL范围仍只能查看本人被指定方案；SELF范围按创建、历史编辑或明确指定关系校验，并继续受部门范围限制。管理员不能替未被指定的登记人填写结果，不能批准自己编辑的方案。摘要用于一致性校验，不能阻止有权限的数据库管理员同时修改数据及摘要。

## 运行页面

截图为当前运行系统的隔离`TEST`记录，默认业务库为空。

| 用户工作台 | 方案与结果 |
|---|---|
| ![登录](docs/screenshots/login.jpg)<br>**登录**：通过会话认证进入工作空间。 | ![设计岗位工作台](docs/screenshots/planner-home.jpg)<br>**设计工作台**：查看权限范围内的方案与状态。 |
| ![实际排样图](docs/screenshots/layout.jpg)<br>**排样图**：查看零件位置、切缝、余料和面积账。 | ![零件需求](docs/screenshots/parts.jpg)<br>**零件需求**：维护尺寸、数量及旋转许可。 |
| ![指定人登记结果](docs/screenshots/results.jpg)<br>**实物结果**：由指定人员登记合格、报废、未切数量。 | ![独立封存结果](docs/screenshots/closed.jpg)<br>**封存结果**：查看独立复核后的实际结局。 |
| ![用户管理](docs/screenshots/users.jpg)<br>**用户管理**：维护账号、部门和岗位。 | ![角色权限](docs/screenshots/roles.jpg)<br>**角色权限**：配置接口权限及数据范围。 |
| ![实际统计](docs/screenshots/dashboard.jpg)<br>**统计**：查看范围内方案状态和人工实物数量。 | ![英文界面](docs/screenshots/english.jpg)<br>**英文界面**：查看英文操作页面。 |
| ![手机界面](docs/screenshots/mobile.jpg)<br>**手机界面**：在窄屏布局中查看和操作方案。 | |

## 技术、结构与数据库

| 部分 | 版本与约束 |
|---|---|
| 后端 | Java21、Maven3.9、Spring Boot4.0.7、Security、JPA、Flyway、MariaDB JDBC3.5.10 |
| 前端 | Node24.19.0、npm11、Vue3.5.40、Vite8.1.5、Lucide1.48.0、ESLint／Prettier |
| 数据库 | MySQL8.4（MySQL8系列），16张身份与业务表及Flyway历史表；结构由版本化SQL创建，JPA仅校验 |
| 部署 | Compose含MySQL、Java服务与Nginx；数据库／后端不映射宿主端口，前端默认回环8132 |
| 时间 | UTC微秒事实，界面Asia/Shanghai；数据库重启不会重新初始化业务 |

```text
backend/src/main/java/cn/zhuatech/sheetnest/   身份、排样算法、业务、接口
backend/src/main/resources/db/migration/     V1身份／V2排样与结果
backend/src/test/                            HTTP/JPA与独立几何测试
frontend/src/                               页面、表单、权限动作、API与测试
frontend/public/brand/                      正式LOGO与原始二维码
docs/                                      手册、架构、接口、部署与实拍截图
scripts/                                   随机配置、隔离验收、素材与敏感检查
compose.yaml                               完整本机部署
```

数据库迁移：`V1__identity.sql`、`V2__sheet_nesting.sql`。初始化总部、5种角色、8项权限、10个菜单、4种材料类型、3项参数和`admin`；不创建方案、零件、排样或实物记录。账号／部门／业务外键保护历史，唯一编号、零件编码、实际结果和请求键受数据库约束。状态、岗位、数量守恒及摘要由事务层复核。

## 先运行完整系统

准备Docker及Compose、Python3.11+；联网构建需官方镜像、Maven Central及npm。

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait
```

访问 [http://127.0.0.1:8132/](http://127.0.0.1:8132/)，健康 [http://127.0.0.1:8132/actuator/health](http://127.0.0.1:8132/actuator/health) 正常包含`"status":"UP"`。初始化登录名`admin`；密码读取被Git忽略的本机`.env`中的`ADMIN_PASSWORD`。配置由脚本随机生成，以0600保存且拒绝覆盖，不提供固定公开密码。更改环境变量不会重置已有库的账号。

建立不同的方案设计、独立复核和实物登记账号，并归入同一负责部门，再创建方案。生产或公开演示不得使用真实客户加工资料。

| 配置名 | 用途 |
|---|---|
| `DATABASE_PASSWORD` | MySQL应用口令，必填 |
| `MYSQL_ROOT_PASSWORD` | 本机数据库管理口令，必填 |
| `ADMIN_PASSWORD` | 只用于空库首次初始化，必填 |
| `WEB_PORT` | 默认8132；端口占用可设`WEB_PORT=18132 docker compose up -d --wait` |
| `BIND_ADDRESS` | 默认127.0.0.1，仅回环 |
| `COOKIE_SECURE` | 本机HTTP false；可信HTTPS对外部署true |
| `DATABASE_URL`／`DATABASE_USER`／`DATABASE_CATALOG` | 后端源码开发可覆盖；Compose内部连接已配置 |

普通停止用`docker compose down`保留卷。仅确认是可丢弃测试库才可使用`down -v`；不要用于原业务库。后端镜像构建执行全部测试，不跳过测试。

### 源码调试

Java21、Maven3.9及已迁移的本机MySQL；为后端进程设置数据库配置与`ADMIN_PASSWORD`后执行：

```bash
cd backend
mvn spring-boot:run
```

另一个终端，在项目根目录使用Node24.19.0／npm11：

```bash
cd frontend
npm ci
npm run dev
```

Vite回环开发端口的`/api`与`/actuator`代理到127.0.0.1:8080。详情见[部署与恢复](docs/部署与恢复.md)。

## 验收与升级

```bash
cd backend
export TEST_ADMIN_PASSWORD="$(python3 -c 'import secrets; print("Aa9" + secrets.token_urlsafe(24))')"
mvn spotless:check test package
unset TEST_ADMIN_PASSWORD
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
python3 scripts/release-check.py
git diff --check
```

后端36项：24项HTTP/JPA状态、范围、撤权、幂等、并发、严格尺寸与人工计数测试；12项算法测试含120组固定种子场景的独立几何校验。前端12项测试覆盖请求与CSRF、岗位状态、显式零值、缺失数量、尺寸精度及单位。隔离MySQL脚本验证7张方案、正常／短缺／停止、退回与修订、范围、并发、CSV、几何和面积账，共2294项断言。

`TEST_ADMIN_PASSWORD`仅供后端测试使用，临时随机生成，不是运行实例的管理员口令；不要写入源码或使用真实业务账号口令。

```bash
# 仅在新建的独立、可丢弃回环测试实例执行；会写入TEST账号和业务
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
# 重启或在独立数据库恢复后验证
python3 scripts/smoke.py --verify
# 独立实例可通过 TEST_URL=http://127.0.0.1:18132 覆盖
```

私有验收状态`output/qa-state.json`包含随机测试口令和比较快照，权限0600且被Git忽略，不发布。升级先备份，在独立环境验证迁移；新增Flyway版本，不修改已发布迁移、不关闭校验或自动修复历史。恢复步骤见[部署与恢复](docs/部署与恢复.md)。

| 常见问题 | 处理 |
|---|---|
| 端口占用 | 覆盖WEB_PORT；不停止其他项目容器 |
| 布局为部分排入 | 查看未排入数量，调整板材、数量、方向许可或最多板数；不能直接送审 |
| 方案改后布局为空 | 输入变更使活动布局失效，重新计算；历史仍在版本页 |
| 无复核／登记账号可选 | 同部门启用账号必须有相应权限，且不能与当前编辑人相同 |
| 结果不能送审 | 每个零件需显式合格／报废／未切，三者合计等于需求；异常须说明 |
| 版本冲突 | 刷新读取最新版本，再重新打开表单 |
| 初始化口令更改无效 | ADMIN_PASSWORD只初始化空库；已有账号通过合法改密流程修改 |
| 构建／迁移失败 | 查看本机受限日志定位并修复；不要跳过测试、清空原库或修改已发布SQL |

安全包括BCrypt12轮、HttpOnly／SameSite Strict Cookie、CSRF、登录失败限流、实时权限和范围、事务锁与版本、有限输入、防SQL拼接及CSV公式注入。对外部署需要可信HTTPS、代理转发头控制、网络访问策略、受限数据库账号、备份和恢复演练。无多租户、外部不可篡改签名或高可用承诺。

## 授权、反馈与联系知华科技

本项目由知华科技（上海如静知华信息科技有限公司）提供公开源码学习版本，主要用于个人学习、技术研究与非商业交流。未经书面授权不得商用。企业信息化建设、中小企业数字化转型、中小企业 AI 转型、私有化部署、软件外包、软件项目外包、软件实施、FDE 外包、OPC 技术支持及深度定制开发，请访问知华科技官网 <https://www.zhuatech.cn/>，或添加微信 zhuatech、zhuatech2 咨询。

贡献参阅[CONTRIBUTING](CONTRIBUTING.md)，一般问题可提交脱敏Issue；安全漏洞通过[SECURITY](SECURITY.md)的官网或微信私下反馈。不要提交口令、客户数据、设备资料或未脱敏备份。本软件为学习与流程研究版本；算法估算、摘要和人工复核不替代实际材料、设备、加工安全与质量判断。

| 咨询微信 zhuatech | 咨询微信 zhuatech2 |
|---|---|
| ![微信 zhuatech](docs/images/wechat-zhuatech.png) | ![微信 zhuatech2](docs/images/wechat-zhuatech2.png) |

官网：[知华科技](https://www.zhuatech.cn/) · 服务：商业授权、定制开发、部署与系统集成。

商业授权或深度定制开发请联系知华科技。
