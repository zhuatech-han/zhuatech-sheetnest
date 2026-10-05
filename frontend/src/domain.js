// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: ["草稿", "Draft"],
  CALCULATED: ["已计算", "Calculated"],
  SUBMITTED: ["方案待复核", "Plan review"],
  APPROVED: ["方案已批准", "Approved"],
  RUNNING: ["实物登记中", "Recording results"],
  REVIEW: ["结果待复核", "Result review"],
  CLOSED: ["已封存", "Closed"],
  CANCELLED: ["已取消", "Cancelled"],
  COMPLETE: ["全部排入", "Fully placed"],
  PARTIAL: ["部分未排入", "Partially placed"],
  COMPLETED: ["全部合格", "All good"],
  SHORTFALL: ["有报废／未切", "Shortfall"],
  STOPPED: ["停止登记", "Stopped"],
};
export const actionNames = {
  calculate: ["计算排样", "Calculate layout"],
  submit: ["提交方案复核", "Submit plan"],
  approve: ["批准并冻结方案", "Approve & freeze"],
  "return-plan": ["退回方案", "Return plan"],
  start: ["开始登记实物结果", "Open result recording"],
  cancel: ["取消方案", "Cancel job"],
  acknowledge: ["确认收悉方案", "Acknowledge plan"],
  "submit-report": ["提交实物报告", "Submit results"],
  "return-result": ["退回结果修订", "Return results"],
  close: ["复核并封存", "Review & close"],
};
/** 岗位与状态决定页面操作，后台仍重新校验实时权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(j) {
  if (!j) return [];
  const a = [];
  if (j.canWrite) {
    if (["DRAFT", "CALCULATED"].includes(j.status)) a.push("calculate");
    if (j.status === "CALCULATED") a.push("submit");
    if (j.status === "APPROVED") a.push("start");
    if (["DRAFT", "CALCULATED", "SUBMITTED", "APPROVED"].includes(j.status))
      a.push("cancel");
  }
  if (j.canReview && j.status === "SUBMITTED") a.push("approve", "return-plan");
  if (j.canReview && j.status === "REVIEW") a.push("close", "return-result");
  if (j.canOperate && j.status === "RUNNING")
    a.push(j.acknowledgedAt ? "submit-report" : "acknowledge");
  return a;
}
/** 只提交表单声明字段，保留零值；拒绝空数量和额外尺寸精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, definitions) {
  return Object.fromEntries(
    definitions.map(([key, , , type]) => {
      let v = form[key];
      if (["id", "integer", "decimal"].includes(type)) {
        const s = String(v ?? "").trim();
        const pattern = type === "decimal" ? /^\d+(?:\.\d)?$/ : /^\d+$/;
        if (!pattern.test(s))
          throw new Error(
            type === "decimal" ? "INVALID_DIMENSION" : "INVALID_INPUT",
          );
        v = Number(s);
        if (
          !Number.isFinite(v) ||
          (type !== "decimal" && !Number.isSafeInteger(v))
        )
          throw new Error("INVALID_INPUT");
      } else if (type === "boolean") v = Boolean(v);
      return [key, v];
    }),
  );
}
/** 绘图与尺寸以整数0.1mm为源，不根据显示值重新计算布局。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function millimeters(ticks) {
  return Number(ticks) / 10;
}
/** 面积源单位0.01mm²，转换平方米仅用于显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function squareMeters(area) {
  return Number(area) / 100000000;
}
