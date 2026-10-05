// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { actions, payload, millimeters, squareMeters } from "./domain.js";
test("closed and cancelled jobs offer no mutations", () => {
  for (const status of ["CLOSED", "CANCELLED"])
    assert.deepEqual(
      actions({ status, canWrite: true, canReview: true, canOperate: true }),
      [],
    );
});
test("assigned operator acknowledges before submission", () => {
  assert.deepEqual(actions({ status: "RUNNING", canOperate: true }), [
    "acknowledge",
  ]);
  assert.deepEqual(
    actions({
      status: "RUNNING",
      canOperate: true,
      acknowledgedAt: "2026-10-06",
    }),
    ["submit-report"],
  );
});
test("independent reviewer can return and seal actual results", () => {
  assert.deepEqual(actions({ status: "REVIEW", canReview: true }), [
    "close",
    "return-result",
  ]);
  assert.deepEqual(actions({ status: "SUBMITTED", canReview: false }), []);
});
test("zero kerf and actual counts remain explicit zero", () => {
  assert.deepEqual(
    payload({ kerf: "0", good: "0" }, [
      ["kerf", "", "", "decimal"],
      ["good", "", "", "integer"],
    ]),
    { kerf: 0, good: 0 },
  );
});
test("missing quantity is not invented", () => {
  assert.throws(
    () => payload({ quantity: "" }, [["quantity", "", "", "integer"]]),
    /INVALID_INPUT/,
  );
});
test("extra decimal precision is rejected rather than rounded", () => {
  assert.throws(
    () => payload({ width: "12.34" }, [["width", "", "", "decimal"]]),
    /INVALID_DIMENSION/,
  );
  assert.equal(
    payload({ width: "12.3" }, [["width", "", "", "decimal"]]).width,
    12.3,
  );
});
test("payload omits client status and hashes", () => {
  assert.deepEqual(
    payload({ name: "A", status: "CLOSED", approvedHash: "fake" }, [["name"]]),
    { name: "A" },
  );
});
test("area and dimension units have independent scale", () => {
  assert.equal(millimeters(1234), 123.4);
  assert.equal(squareMeters(200000000), 2);
});
test("fractional actual pieces cannot be submitted", () => {
  assert.throws(
    () => payload({ good: "1.5" }, [["good", "", "", "integer"]]),
    /INVALID_INPUT/,
  );
});
