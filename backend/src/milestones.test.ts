import { describe, expect, it } from "vitest";

import {
  MAX_CARD_LENGTH,
  SYSTEM_USER_ID,
  buildMilestoneCards,
  detectPersonalRecord,
  estimateOneRepMax,
  formatPrMilestone,
  formatSessionMilestone,
  parseSetsJson,
} from "./milestones";

const PRIOR_BENCH_100X5 = JSON.stringify([
  { exerciseId: "bench_press", setIndex: 0, reps: 5, weightKg: 100 },
]);

describe("estimateOneRepMax", () => {
  it("applies the Epley formula", () => {
    expect(estimateOneRepMax(100, 10)).toBeCloseTo(133.3333, 3);
  });

  it("supports fractional weights", () => {
    expect(estimateOneRepMax(82.5, 5)).toBeCloseTo(96.25, 6);
  });

  it("guards zero, negative and non-finite input", () => {
    expect(estimateOneRepMax(100, 0)).toBe(0);
    expect(estimateOneRepMax(0, 10)).toBe(0);
    expect(estimateOneRepMax(-100, 5)).toBe(0);
    expect(estimateOneRepMax(100, -5)).toBe(0);
    expect(estimateOneRepMax(Number.NaN, 5)).toBe(0);
    expect(estimateOneRepMax(100, Number.POSITIVE_INFINITY)).toBe(0);
  });
});

describe("detectPersonalRecord", () => {
  it("produces no PR on the first-ever session (baseline only)", () => {
    expect(
      detectPersonalRecord(
        [{ exerciseId: "bench_press", reps: 8, weightKg: 100 }],
        [],
      ),
    ).toEqual([]);
  });

  it("requires a strict beat: an identical set is not a PR", () => {
    expect(
      detectPersonalRecord(
        [{ exerciseId: "bench_press", reps: 5, weightKg: 100 }],
        [PRIOR_BENCH_100X5],
      ),
    ).toEqual([]);
  });

  it("detects a PR when an added rep raises the estimated 1RM", () => {
    const records = detectPersonalRecord(
      [{ exerciseId: "bench_press", reps: 6, weightKg: 100 }],
      [PRIOR_BENCH_100X5],
    );

    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({
      exerciseId: "bench_press",
      weightKg: 100,
      reps: 6,
    });
    expect(records[0]?.estimatedOneRepMaxKg).toBeCloseTo(120, 6);
    expect(records[0]?.previousEstimatedOneRepMaxKg).toBeCloseTo(116.6667, 3);
  });

  it("treats a lower top weight with a higher estimated 1RM as a PR", () => {
    const prior = JSON.stringify([
      { exerciseId: "squat", setIndex: 0, reps: 1, weightKg: 120 },
    ]);
    const records = detectPersonalRecord(
      [{ exerciseId: "squat", reps: 10, weightKg: 100 }],
      [prior],
    );

    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({ exerciseId: "squat", weightKg: 100, reps: 10 });
    expect(records[0]?.estimatedOneRepMaxKg).toBeCloseTo(133.3333, 3);
  });

  it("uses only the best set per exercise in the current session", () => {
    const records = detectPersonalRecord(
      [
        { exerciseId: "bench_press", setIndex: 0, reps: 5, weightKg: 100 },
        { exerciseId: "bench_press", setIndex: 1, reps: 8, weightKg: 100 },
      ],
      [PRIOR_BENCH_100X5],
    );

    expect(records).toHaveLength(1);
    expect(records[0]?.reps).toBe(8);
  });

  it("guards empty sessions, zero reps and zero weight", () => {
    expect(detectPersonalRecord([], [PRIOR_BENCH_100X5])).toEqual([]);
    expect(
      detectPersonalRecord(
        [{ exerciseId: "bench_press", reps: 0, weightKg: 200 }],
        [PRIOR_BENCH_100X5],
      ),
    ).toEqual([]);
    expect(
      detectPersonalRecord(
        [{ exerciseId: "bench_press", reps: 5, weightKg: 0 }],
        [PRIOR_BENCH_100X5],
      ),
    ).toEqual([]);
  });

  it("ignores malformed history rows instead of throwing", () => {
    expect(
      detectPersonalRecord([{ exerciseId: "bench_press", reps: 6, weightKg: 100 }], [
        "not-json",
        "{}",
        JSON.stringify([{ exerciseId: "bench_press", reps: "six", weightKg: 100 }]),
      ]),
    ).toEqual([]);
  });
});

describe("parseSetsJson", () => {
  it("parses valid sets and drops unknown rows", () => {
    const sets = parseSetsJson(
      JSON.stringify([
        { exerciseId: "bench_press", setIndex: 0, reps: 5, weightKg: 100 },
        { exerciseId: 42, reps: 5, weightKg: 100 },
        { exerciseId: "squat", reps: 5, weightKg: "heavy" },
      ]),
    );

    expect(sets).toHaveLength(1);
    expect(sets[0]).toMatchObject({ exerciseId: "bench_press", reps: 5, weightKg: 100 });
  });

  it("returns an empty array for null and malformed input", () => {
    expect(parseSetsJson(null)).toEqual([]);
    expect(parseSetsJson(undefined)).toEqual([]);
    expect(parseSetsJson("{")).toEqual([]);
    expect(parseSetsJson('{"sets":[]}')).toEqual([]);
  });
});

describe("formatSessionMilestone", () => {
  it("formats volume with thousands separators", () => {
    const body = formatSessionMilestone({
      displayName: "Dana",
      title: "Upper Push",
      sets: [
        { exerciseId: "bench_press", reps: 10, weightKg: 100 },
        { exerciseId: "bench_press", reps: 9, weightKg: 100 },
        { exerciseId: "bench_press", reps: 10, weightKg: 90 },
      ],
    });

    expect(body).toBe("Dana completed Upper Push | 2,800 kg total volume across 3 sets");
  });

  it("uses the singular form for a single set", () => {
    const body = formatSessionMilestone({
      displayName: "Dana",
      title: "Upper Push",
      sets: [{ exerciseId: "bench_press", reps: 5, weightKg: 100 }],
    });

    expect(body).toContain("across 1 set");
  });

  it("truncates oversized cards to the squad chat limit", () => {
    const body = formatSessionMilestone({
      displayName: "D".repeat(600),
      title: "Upper Push",
      sets: [{ exerciseId: "bench_press", reps: 5, weightKg: 100 }],
    });

    expect(body.length).toBe(MAX_CARD_LENGTH);
    expect(body.endsWith("...")).toBe(true);
  });
});

describe("formatPrMilestone", () => {
  it("formats the best set and estimated 1RM", () => {
    const [record] = detectPersonalRecord(
      [{ exerciseId: "bench_press", reps: 6, weightKg: 100 }],
      [PRIOR_BENCH_100X5],
    );
    expect(record).toBeDefined();

    expect(formatPrMilestone(record!, "Dana")).toBe(
      "Dana set a new PR on bench_press | 100 kg x 6 reps (est. 1RM 120 kg)",
    );
  });

  it("keeps fractional weights in the card", () => {
    const prior = JSON.stringify([
      { exerciseId: "bench_press", reps: 5, weightKg: 82.5 },
    ]);
    const [record] = detectPersonalRecord(
      [{ exerciseId: "bench_press", reps: 6, weightKg: 82.5 }],
      [prior],
    );
    expect(record).toBeDefined();

    expect(formatPrMilestone(record!, "Dana")).toContain("82.5 kg x 6 reps");
  });
});

describe("buildMilestoneCards", () => {
  it("uses the system user id for every card", () => {
    expect(SYSTEM_USER_ID).toBe("system");

    const cards = buildMilestoneCards({
      displayName: "Dana",
      sessionTitle: "Upper Push",
      sets: [{ exerciseId: "bench_press", reps: 6, weightKg: 100 }],
      priorSetsJson: [PRIOR_BENCH_100X5],
    });

    expect(cards).toHaveLength(2);
    expect(cards.map((card) => card.userId)).toEqual(["system", "system"]);
    expect(cards[0]?.body).toContain("completed Upper Push");
    expect(cards[1]?.body).toContain("set a new PR on bench_press");
  });

  it("emits only the completion card when history is empty", () => {
    const cards = buildMilestoneCards({
      displayName: "Dana",
      sessionTitle: "Upper Push",
      sets: [{ exerciseId: "bench_press", reps: 6, weightKg: 100 }],
      priorSetsJson: [],
    });

    expect(cards).toHaveLength(1);
    expect(cards[0]?.body).not.toContain("new PR");
  });

  it("emits at most two cards and picks the biggest PR", () => {
    const priorBench = JSON.stringify([
      { exerciseId: "bench_press", reps: 5, weightKg: 100 },
    ]);
    const priorSquat = JSON.stringify([
      { exerciseId: "squat", reps: 1, weightKg: 120 },
    ]);

    const cards = buildMilestoneCards({
      displayName: "Dana",
      sessionTitle: "Full Body",
      sets: [
        { exerciseId: "bench_press", reps: 5, weightKg: 105 },
        { exerciseId: "squat", reps: 1, weightKg: 130 },
      ],
      priorSetsJson: [priorBench, priorSquat],
    });

    expect(cards).toHaveLength(2);
    expect(cards[1]?.body).toContain("squat");
  });

  it("emits nothing for an empty session", () => {
    expect(
      buildMilestoneCards({
        displayName: "Dana",
        sessionTitle: "Upper Push",
        sets: [],
        priorSetsJson: [PRIOR_BENCH_100X5],
      }),
    ).toEqual([]);
  });
});
