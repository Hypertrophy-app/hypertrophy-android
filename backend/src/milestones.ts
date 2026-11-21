/** Cards longer than the squad chat limit are truncated by the Durable Object. */
export const MAX_CARD_LENGTH = 500;

/** Chat cards are attributed to this reserved user id. */
export const SYSTEM_USER_ID = "system";

export interface SyncSet {
  exerciseId: string;
  setIndex?: number;
  reps: number;
  weightKg: number;
}

export interface PersonalRecordHit {
  exerciseId: string;
  weightKg: number;
  reps: number;
  estimatedOneRepMaxKg: number;
  previousEstimatedOneRepMaxKg: number;
}

export interface MilestoneCard {
  userId: string;
  body: string;
}

/** Epley estimate. Guards against zero, negative and non-finite input. */
export function estimateOneRepMax(weightKg: number, reps: number): number {
  if (!Number.isFinite(weightKg) || !Number.isFinite(reps)) return 0;
  if (weightKg <= 0 || reps <= 0) return 0;
  return weightKg * (1 + reps / 30);
}

export function parseSetsJson(input: string | null | undefined): SyncSet[] {
  if (typeof input !== "string" || input.length === 0) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(input);
  } catch {
    return [];
  }
  if (!Array.isArray(parsed)) return [];

  const sets: SyncSet[] = [];
  for (const row of parsed) {
    if (typeof row !== "object" || row === null) continue;
    const record = row as Record<string, unknown>;
    const exerciseId = record.exerciseId;
    const reps = record.reps;
    const weightKg = record.weightKg;
    if (typeof exerciseId !== "string" || exerciseId.length === 0) continue;
    if (typeof reps !== "number" || !Number.isFinite(reps)) continue;
    if (typeof weightKg !== "number" || !Number.isFinite(weightKg)) continue;
    sets.push({ exerciseId, reps, weightKg });
  }
  return sets;
}

interface BestSet {
  weightKg: number;
  reps: number;
  estimatedOneRepMaxKg: number;
}

function bestSetByExercise(sets: SyncSet[]): Map<string, BestSet> {
  const best = new Map<string, BestSet>();
  for (const set of sets) {
    const estimated = estimateOneRepMax(set.weightKg, set.reps);
    if (estimated <= 0) continue;
    const current = best.get(set.exerciseId);
    if (!current || estimated > current.estimatedOneRepMaxKg) {
      best.set(set.exerciseId, {
        weightKg: set.weightKg,
        reps: set.reps,
        estimatedOneRepMaxKg: estimated,
      });
    }
  }
  return best;
}

/**
 * Compares the best set per exercise in the current session against the best
 * estimated 1RM found in prior session payloads. A tie is not a PR; the first
 * ever session only establishes a baseline.
 */
export function detectPersonalRecord(
  currentSets: SyncSet[],
  priorSetsJson: Array<string | null | undefined>,
): PersonalRecordHit[] {
  const priorBest = new Map<string, number>();
  for (const json of priorSetsJson) {
    for (const [exerciseId, best] of bestSetByExercise(parseSetsJson(json))) {
      const current = priorBest.get(exerciseId) ?? 0;
      if (best.estimatedOneRepMaxKg > current) {
        priorBest.set(exerciseId, best.estimatedOneRepMaxKg);
      }
    }
  }

  const hits: PersonalRecordHit[] = [];
  for (const [exerciseId, best] of bestSetByExercise(currentSets ?? [])) {
    const previous = priorBest.get(exerciseId) ?? 0;
    if (previous <= 0 || best.estimatedOneRepMaxKg <= previous) continue;
    hits.push({
      exerciseId,
      weightKg: best.weightKg,
      reps: best.reps,
      estimatedOneRepMaxKg: best.estimatedOneRepMaxKg,
      previousEstimatedOneRepMaxKg: previous,
    });
  }
  return hits;
}

function clampCard(body: string): string {
  if (body.length <= MAX_CARD_LENGTH) return body;
  return `${body.slice(0, MAX_CARD_LENGTH - 3)}...`;
}

function formatKg(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

function formatVolume(value: number): string {
  const rounded = Math.round(Number.isFinite(value) ? value : 0);
  return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

export function formatSessionMilestone(input: {
  displayName: string;
  title: string;
  sets: SyncSet[];
}): string {
  const name = (input.displayName || "").trim() || "An athlete";
  const title = (input.title || "").trim() || "a workout";
  const totalVolume = (input.sets ?? []).reduce(
    (sum, set) => sum + (Number.isFinite(set.reps) ? set.reps : 0) * (Number.isFinite(set.weightKg) ? set.weightKg : 0),
    0,
  );
  const totalSets = (input.sets ?? []).length;
  const setLabel = totalSets === 1 ? "set" : "sets";
  return clampCard(
    `${name} completed ${title} | ${formatVolume(totalVolume)} kg total volume across ${totalSets} ${setLabel}`,
  );
}

export function formatPrMilestone(record: PersonalRecordHit, displayName: string): string {
  const name = (displayName || "").trim() || "An athlete";
  return clampCard(
    `${name} set a new PR on ${record.exerciseId} | ${formatKg(record.weightKg)} kg x ${record.reps} reps (est. 1RM ${formatKg(record.estimatedOneRepMaxKg)} kg)`,
  );
}

/**
 * Produces at most two cards per session: one completion card and, when the
 * session beat prior history, one card for the biggest personal record.
 */
export function buildMilestoneCards(input: {
  displayName: string;
  sessionTitle: string;
  sets: SyncSet[];
  priorSetsJson: Array<string | null | undefined>;
}): MilestoneCard[] {
  const sets = input.sets ?? [];
  if (sets.length === 0) return [];

  const cards: MilestoneCard[] = [
    {
      userId: SYSTEM_USER_ID,
      body: formatSessionMilestone({
        displayName: input.displayName,
        title: input.sessionTitle,
        sets,
      }),
    },
  ];

  const records = detectPersonalRecord(sets, input.priorSetsJson ?? []);
  if (records.length > 0) {
    const biggest = records.reduce((best, record) =>
      record.estimatedOneRepMaxKg > best.estimatedOneRepMaxKg ? record : best,
    );
    cards.push({ userId: SYSTEM_USER_ID, body: formatPrMilestone(biggest, input.displayName) });
  }

  return cards;
}
