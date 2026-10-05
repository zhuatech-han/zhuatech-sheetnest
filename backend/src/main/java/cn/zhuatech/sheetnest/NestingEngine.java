// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.sheetnest;

import java.math.*;
import java.util.*;

/** 自主整数贯穿切割启发式，生成可复算分割树；不保证最优或机器可执行性。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class NestingEngine {
  private NestingEngine() {}

  public static final String VERSION = "GUILLOTINE-PORTFOLIO-1";

  /** 输入尺寸为0.1mm整数，数量与允许旋转由方案显式定义。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Item(long id, int width, int height, int quantity, boolean rotation) {}

  /** 分割树中每个节点表示当前完整矩形，切缝面积由父节点扣除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Tree(
      String path,
      String kind,
      int x,
      int y,
      int width,
      int height,
      Long partId,
      Integer piece,
      Boolean rotated,
      String axis,
      Integer at,
      Integer kerf,
      List<Tree> children) {}

  /** 位置信息只作排样与复核，不能作为机床代码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Placement(
      long partId, int piece, int x, int y, int width, int height, boolean rotated) {}

  /** 贯穿当前子板的逻辑分割，不包含进给、装夹或安全参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Cut(
      String path, String axis, int at, int kerf, int x, int y, int width, int height) {}

  /** 剩余矩形保留几何事实，不自动成为库存或可售余料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Offcut(int x, int y, int width, int height) {}

  /** 每张使用板材的零件、切缝和余料面积守恒。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Board(
      int number,
      Tree tree,
      List<Placement> placements,
      List<Cut> cuts,
      List<Offcut> offcuts,
      long partArea,
      long kerfArea,
      long leftoverArea) {}

  /** 未排入数量明确保留，不以空白或零代替成功。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Unplaced(long partId, int quantity) {}

  /** 八种确定性顺序与分割策略比较结果；全部面积以0.01mm²计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Result(
      String algorithm,
      String strategy,
      String status,
      String unit,
      int width,
      int height,
      int margin,
      int kerf,
      int requested,
      int placed,
      int usedSheets,
      long sheetArea,
      long usableArea,
      long partArea,
      long kerfArea,
      long leftoverArea,
      long trimArea,
      BigDecimal utilization,
      List<Board> boards,
      List<Unplaced> unplaced) {}

  private record Piece(long id, int instance, int width, int height, boolean rotation) {}

  private static final class Node {
    String path;
    int x, y, w, h;
    Piece part;
    boolean rotated;
    String axis;
    int at, kerf;
    List<Node> children = new ArrayList<>();

    Node(String path, int x, int y, int w, int h) {
      this.path = path;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }

    long area() {
      return (long) w * h;
    }

    Tree tree() {
      return new Tree(
          path,
          part != null ? "PART" : axis != null ? "CUT" : "OFFCUT",
          x,
          y,
          w,
          h,
          part == null ? null : part.id,
          part == null ? null : part.instance,
          part == null ? null : rotated,
          axis,
          axis == null ? null : at,
          axis == null ? null : kerf,
          children.stream().map(Node::tree).toList());
    }
  }

  private static final class Plate {
    int number;
    Node root;
    List<Node> free = new ArrayList<>();

    Plate(int number, int width, int height, int margin) {
      this.number = number;
      root = new Node("R", margin, margin, width - 2 * margin, height - 2 * margin);
      free.add(root);
    }
  }

  private record Fit(Plate plate, Node node, int width, int height, boolean rotated) {
    int shortSide() {
      return Math.min(node.w - width, node.h - height);
    }

    int longSide() {
      return Math.max(node.w - width, node.h - height);
    }
  }

  private static void valid(boolean ok) {
    if (!ok) throw new Problem(400, "INVALID_DESIGN");
  }

  /** 校验有界输入并比较八种固定启发式，相同输入产生相同结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Result calculate(
      int width, int height, int margin, int kerf, int maximum, List<Item> items) {
    valid(
        width >= 10
            && width <= 500000
            && height >= 10
            && height <= 500000
            && margin >= 0
            && margin <= 20000
            && 2 * margin < width
            && 2 * margin < height
            && kerf >= 0
            && kerf <= 1000
            && maximum >= 1
            && maximum <= 30
            && items != null
            && !items.isEmpty()
            && items.size() <= 30);
    int count = 0;
    var ids = new HashSet<Long>();
    for (var p : items) {
      valid(
          p != null
              && p.id > 0
              && ids.add(p.id)
              && p.width >= 10
              && p.width <= 500000
              && p.height >= 10
              && p.height <= 500000
              && p.quantity >= 1
              && p.quantity <= 300);
      count += p.quantity;
    }
    valid(count <= 300);
    Result best = null;
    for (int order = 0; order < 4; order++)
      for (int split = 0; split < 2; split++) {
        var r = run(width, height, margin, kerf, maximum, items, order, split);
        if (best == null || rank(r, best) < 0) best = r;
      }
    return best;
  }

  private static int rank(Result a, Result b) {
    int c = Integer.compare(b.placed, a.placed);
    if (c != 0) return c;
    c = Integer.compare(a.usedSheets, b.usedSheets);
    if (c != 0) return c;
    c = Long.compare(a.kerfArea, b.kerfArea);
    return c != 0 ? c : a.strategy.compareTo(b.strategy);
  }

  private static Comparator<Piece> ordering(int order) {
    Comparator<Piece> c =
        switch (order) {
          case 0 -> Comparator.comparingLong((Piece p) -> (long) p.width * p.height).reversed();
          case 1 -> Comparator.comparingInt((Piece p) -> Math.max(p.width, p.height)).reversed();
          case 2 -> Comparator.comparingInt(Piece::width).reversed();
          default -> Comparator.comparingInt(Piece::height).reversed();
        };
    return c.thenComparingLong(Piece::id).thenComparingInt(Piece::instance);
  }

  private static boolean fits(Node n, int w, int h, int kerf) {
    return w <= n.w && h <= n.h && (n.w == w || n.w - w >= kerf) && (n.h == h || n.h - h >= kerf);
  }

  private static Fit find(List<Plate> plates, Piece p, int kerf) {
    var choices = new ArrayList<Fit>();
    for (var plate : plates)
      for (var n : plate.free) {
        if (fits(n, p.width, p.height, kerf))
          choices.add(new Fit(plate, n, p.width, p.height, false));
        if (p.rotation && p.width != p.height && fits(n, p.height, p.width, kerf))
          choices.add(new Fit(plate, n, p.height, p.width, true));
      }
    return choices.stream()
        .min(
            Comparator.comparingInt(Fit::shortSide)
                .thenComparingInt(Fit::longSide)
                .thenComparingInt(f -> f.plate.number)
                .thenComparing(f -> f.node.path)
                .thenComparing(Fit::rotated))
        .orElse(null);
  }

  private static Node split(Node n, String axis, int size, int kerf, List<Node> free) {
    int full = axis.equals("V") ? n.w : n.h;
    if (size == full) return n;
    n.axis = axis;
    n.at = (axis.equals("V") ? n.x : n.y) + size;
    n.kerf = kerf;
    var kept =
        new Node(
            n.path + "/0", n.x, n.y, axis.equals("V") ? size : n.w, axis.equals("H") ? size : n.h);
    n.children.add(kept);
    int remainder = full - size - kerf;
    if (remainder > 0) {
      var rest =
          new Node(
              n.path + "/1",
              axis.equals("V") ? n.x + size + kerf : n.x,
              axis.equals("H") ? n.y + size + kerf : n.y,
              axis.equals("V") ? remainder : n.w,
              axis.equals("H") ? remainder : n.h);
      n.children.add(rest);
      free.add(rest);
    }
    return kept;
  }

  private static void place(Fit f, Piece p, int kerf, int orientation) {
    var free = f.plate.free;
    free.remove(f.node);
    Node used;
    if (orientation == 0) {
      used = split(f.node, "V", f.width, kerf, free);
      used = split(used, "H", f.height, kerf, free);
    } else {
      used = split(f.node, "H", f.height, kerf, free);
      used = split(used, "V", f.width, kerf, free);
    }
    used.part = p;
    used.rotated = f.rotated;
  }

  private static void collect(
      Node n, List<Placement> placements, List<Cut> cuts, List<Offcut> offcuts) {
    if (n.part != null)
      placements.add(new Placement(n.part.id, n.part.instance, n.x, n.y, n.w, n.h, n.rotated));
    else if (n.axis != null) {
      cuts.add(new Cut(n.path, n.axis, n.at, n.kerf, n.x, n.y, n.w, n.h));
      for (var child : n.children) collect(child, placements, cuts, offcuts);
    } else offcuts.add(new Offcut(n.x, n.y, n.w, n.h));
  }

  private static Result run(
      int width,
      int height,
      int margin,
      int kerf,
      int max,
      List<Item> items,
      int order,
      int split) {
    var pieces = new ArrayList<Piece>();
    for (var p : items)
      for (int i = 1; i <= p.quantity; i++)
        pieces.add(new Piece(p.id, i, p.width, p.height, p.rotation));
    pieces.sort(ordering(order));
    var plates = new ArrayList<Plate>();
    var missing = new TreeMap<Long, Integer>();
    for (var p : pieces) {
      Fit f = find(plates, p, kerf);
      if (f == null && plates.size() < max) {
        var next = new Plate(plates.size() + 1, width, height, margin);
        f = find(List.of(next), p, kerf);
        if (f != null) plates.add(next);
      }
      if (f == null) missing.merge(p.id, 1, Integer::sum);
      else place(f, p, kerf, split);
    }
    var boards = new ArrayList<Board>();
    long partArea = 0, kerfArea = 0, leftover = 0;
    int placed = 0;
    for (var plate : plates) {
      var placements = new ArrayList<Placement>();
      var cuts = new ArrayList<Cut>();
      var offcuts = new ArrayList<Offcut>();
      collect(plate.root, placements, cuts, offcuts);
      long pa = placements.stream().mapToLong(p -> (long) p.width * p.height).sum();
      long ka =
          cuts.stream()
              .mapToLong(c -> (long) c.kerf * (c.axis.equals("V") ? c.height : c.width))
              .sum();
      long oa = offcuts.stream().mapToLong(o -> (long) o.width * o.height).sum();
      if (pa + ka + oa != plate.root.area()) throw new IllegalStateException("AREA_CONSERVATION");
      partArea += pa;
      kerfArea += ka;
      leftover += oa;
      placed += placements.size();
      boards.add(
          new Board(
              plate.number,
              plate.root.tree(),
              List.copyOf(placements),
              List.copyOf(cuts),
              List.copyOf(offcuts),
              pa,
              ka,
              oa));
    }
    long total = (long) width * height * boards.size(),
        usable = (long) (width - 2 * margin) * (height - 2 * margin) * boards.size();
    BigDecimal ratio =
        total == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(partArea)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    return new Result(
        VERSION,
        "ORDER-" + order + "/SPLIT-" + split,
        missing.isEmpty() ? "COMPLETE" : "PARTIAL",
        "0.1mm",
        width,
        height,
        margin,
        kerf,
        pieces.size(),
        placed,
        boards.size(),
        total,
        usable,
        partArea,
        kerfArea,
        leftover,
        total - usable,
        ratio,
        List.copyOf(boards),
        missing.entrySet().stream().map(e -> new Unplaced(e.getKey(), e.getValue())).toList());
  }
}
