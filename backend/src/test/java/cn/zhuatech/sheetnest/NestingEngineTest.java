// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 独立几何与数量检查，不靠算法自身的面积断言。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class NestingEngineTest {
  private NestingEngine.Item item(long id, int w, int h, int q, boolean rotate) {
    return new NestingEngine.Item(id, w, h, q, rotate);
  }

  private long area(NestingEngine.Tree t) {
    return (long) t.width() * t.height();
  }

  private void tree(NestingEngine.Tree t) {
    assertTrue(t.width() > 0 && t.height() > 0);
    if (t.kind().equals("CUT")) {
      assertTrue(t.children().size() >= 1 && t.children().size() <= 2);
      long loss = (long) t.kerf() * (t.axis().equals("V") ? t.height() : t.width());
      assertEquals(area(t), t.children().stream().mapToLong(this::area).sum() + loss);
      var kept = t.children().getFirst();
      assertEquals(t.x(), kept.x());
      assertEquals(t.y(), kept.y());
      if (t.axis().equals("V")) {
        assertEquals(t.height(), kept.height());
        assertEquals(t.at() - t.x(), kept.width());
      } else {
        assertEquals(t.width(), kept.width());
        assertEquals(t.at() - t.y(), kept.height());
      }
      if (t.children().size() == 2) {
        var rest = t.children().getLast();
        if (t.axis().equals("V")) {
          assertEquals(t.at() + t.kerf(), rest.x());
          assertEquals(t.y(), rest.y());
          assertEquals(t.height(), rest.height());
          assertEquals(t.x() + t.width(), rest.x() + rest.width());
        } else {
          assertEquals(t.at() + t.kerf(), rest.y());
          assertEquals(t.x(), rest.x());
          assertEquals(t.width(), rest.width());
          assertEquals(t.y() + t.height(), rest.y() + rest.height());
        }
      }
      t.children().forEach(this::tree);
    } else assertTrue(t.children().isEmpty());
  }

  private void geometry(NestingEngine.Result r, List<NestingEngine.Item> items) {
    var seen = new HashSet<String>();
    var counts = new HashMap<Long, Integer>();
    int total = 0;
    for (var b : r.boards()) {
      tree(b.tree());
      long area = 0;
      for (var p : b.placements()) {
        assertTrue(seen.add(p.partId() + "/" + p.piece()));
        var spec = items.stream().filter(i -> i.id() == p.partId()).findFirst().orElseThrow();
        assertTrue(p.piece() >= 1 && p.piece() <= spec.quantity());
        assertTrue(!p.rotated() || spec.rotation());
        assertEquals(p.rotated() ? spec.height() : spec.width(), p.width());
        assertEquals(p.rotated() ? spec.width() : spec.height(), p.height());
        assertTrue(
            p.x() >= r.margin()
                && p.y() >= r.margin()
                && p.x() + p.width() <= r.width() - r.margin()
                && p.y() + p.height() <= r.height() - r.margin());
        area += (long) p.width() * p.height();
        counts.merge(p.partId(), 1, Integer::sum);
        total++;
      }
      for (int i = 0; i < b.placements().size(); i++)
        for (int j = i + 1; j < b.placements().size(); j++) {
          var a = b.placements().get(i);
          var c = b.placements().get(j);
          assertTrue(
              a.x() + a.width() + r.kerf() <= c.x()
                  || c.x() + c.width() + r.kerf() <= a.x()
                  || a.y() + a.height() + r.kerf() <= c.y()
                  || c.y() + c.height() + r.kerf() <= a.y());
        }
      assertEquals(area, b.partArea());
      assertEquals(
          (long) (r.width() - 2 * r.margin()) * (r.height() - 2 * r.margin()),
          b.partArea() + b.kerfArea() + b.leftoverArea());
    }
    for (var x : r.unplaced()) counts.merge(x.partId(), x.quantity(), Integer::sum);
    for (var x : items) assertEquals(x.quantity(), counts.getOrDefault(x.id(), 0));
    assertEquals(total, r.placed());
    assertEquals(r.sheetArea(), r.partArea() + r.kerfArea() + r.leftoverArea() + r.trimArea());
  }

  @Test
  void exactBoardHasNoInventedCutOrWaste() {
    var r = NestingEngine.calculate(1000, 500, 0, 30, 1, List.of(item(1, 1000, 500, 1, false)));
    assertEquals("COMPLETE", r.status());
    assertEquals(0, r.kerfArea());
    assertEquals(0, r.leftoverArea());
    assertEquals(0, r.boards().getFirst().cuts().size());
    assertEquals(0, new BigDecimal("100").compareTo(r.utilization()));
  }

  @Test
  void fullKerfConsumesRemainingStrip() {
    var r = NestingEngine.calculate(1030, 500, 0, 30, 1, List.of(item(1, 1000, 500, 1, false)));
    assertEquals(15000, r.kerfArea());
    assertEquals(0, r.leftoverArea());
    geometry(r, List.of(item(1, 1000, 500, 1, false)));
  }

  @Test
  void insufficientFullKerfIsConservativelyRejected() {
    var r = NestingEngine.calculate(1020, 500, 0, 30, 1, List.of(item(1, 1000, 500, 1, false)));
    assertEquals("PARTIAL", r.status());
    assertEquals(0, r.usedSheets());
    assertEquals(1, r.unplaced().getFirst().quantity());
  }

  @Test
  void marginReducesUsableArea() {
    var r = NestingEngine.calculate(1020, 520, 10, 0, 1, List.of(item(1, 1000, 500, 1, false)));
    assertEquals(30400, r.trimArea());
    assertEquals(500000, r.partArea());
    geometry(r, List.of(item(1, 1000, 500, 1, false)));
  }

  @Test
  void rotationMustBeExplicitlyAllowed() {
    var a = NestingEngine.calculate(500, 1000, 0, 0, 1, List.of(item(1, 1000, 500, 1, false)));
    assertEquals(0, a.placed());
    var b = NestingEngine.calculate(500, 1000, 0, 0, 1, List.of(item(1, 1000, 500, 1, true)));
    assertEquals(1, b.placed());
    assertTrue(b.boards().getFirst().placements().getFirst().rotated());
  }

  @Test
  void capacityDoesNotInventAdditionalSheets() {
    var r = NestingEngine.calculate(1000, 500, 0, 0, 2, List.of(item(1, 1000, 500, 3, false)));
    assertEquals(2, r.usedSheets());
    assertEquals(1, r.unplaced().getFirst().quantity());
  }

  @Test
  void impossibleLargePieceDoesNotBlockSmallerPiece() {
    var items = List.of(item(1, 1100, 500, 1, false), item(2, 500, 500, 1, false));
    var r = NestingEngine.calculate(1000, 500, 0, 10, 1, items);
    assertEquals(1, r.usedSheets());
    assertEquals(1, r.placed());
    assertEquals(1, r.unplaced().getFirst().partId());
    geometry(r, items);
  }

  @Test
  void zeroKerfIsExplicitNotMissing() {
    var r = NestingEngine.calculate(1000, 500, 0, 0, 1, List.of(item(1, 500, 500, 2, false)));
    assertEquals(2, r.placed());
    assertEquals(0, r.kerfArea());
  }

  @Test
  void sameInputAlwaysHasSameTreeAndStrategy() {
    var items = List.of(item(1, 500, 200, 3, true), item(2, 100, 700, 2, false));
    assertEquals(
        NestingEngine.calculate(1000, 1000, 10, 25, 3, items),
        NestingEngine.calculate(1000, 1000, 10, 25, 3, items));
  }

  @Test
  void invalidInputIsRejected() {
    assertThrows(
        Problem.class,
        () -> NestingEngine.calculate(0, 100, 0, 0, 1, List.of(item(1, 10, 10, 1, true))));
    assertThrows(
        Problem.class,
        () -> NestingEngine.calculate(100, 100, 50, 0, 1, List.of(item(1, 10, 10, 1, true))));
    assertThrows(
        Problem.class,
        () -> NestingEngine.calculate(100, 100, 0, -1, 1, List.of(item(1, 10, 10, 1, true))));
    assertThrows(
        Problem.class,
        () -> NestingEngine.calculate(100, 100, 0, 0, 31, List.of(item(1, 10, 10, 1, true))));
    assertThrows(
        Problem.class,
        () ->
            NestingEngine.calculate(
                100, 100, 0, 0, 1, List.of(item(1, 10, 10, 1, true), item(1, 20, 20, 1, true))));
    assertThrows(
        Problem.class,
        () ->
            NestingEngine.calculate(
                100, 100, 0, 0, 1, List.of(item(1, 10, 10, 300, true), item(2, 10, 10, 1, true))));
  }

  @Test
  void largeDimensionsUseLongArea() {
    var r =
        NestingEngine.calculate(
            500000, 500000, 0, 0, 1, List.of(item(1, 500000, 500000, 1, false)));
    assertEquals(250000000000L, r.partArea());
  }

  @Test
  void generatedGeometryMaintainsIndependentInvariants() {
    var random = new Random(6291);
    for (int n = 0; n < 120; n++) {
      var items = new ArrayList<NestingEngine.Item>();
      for (int j = 0; j < 8; j++)
        items.add(
            item(
                j + 1,
                10 + random.nextInt(700),
                10 + random.nextInt(700),
                1 + random.nextInt(6),
                random.nextBoolean()));
      var r = NestingEngine.calculate(900, 1100, random.nextInt(30), random.nextInt(30), 6, items);
      geometry(r, items);
    }
  }
}
