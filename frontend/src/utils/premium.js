/**
 * Ước tính phí FE — đồng bộ đúng công thức PremiumCalculationService.java (BE):
 *   premium = max(MIN_PREMIUM, limit × baseRate/100 × termFactor × typeFactor × deductibleAdj)
 *   termFactor: 12M = 1, 24M = 1.85, 36M = 2.65, kỳ hạn khác fallback termMonths/12
 *   typeFactor: STANDARD = 1, ENHANCED = 1.25, COMPREHENSIVE = 1.5, giá trị lạ → 1.0
 *   deductibleAdj: 1 − 0.35 × (1 − e^(−8 × deductible/limit)) khi deductible > 0
 *   limit ≤ 0 hoặc baseRate không hợp lệ → 0 (BE compute() trả 0.0).
 */

/** Base rate catalog — đồng bộ BE BASE_RATES (PROP_WATER 0.06, WCOMP 0.13). */
export const COVERAGE_RATES = {
  PROP_FIRE: 0.075,
  PROP_WATER: 0.06,
  LIAB_PUBLIC: 0.12,
  WCOMP: 0.13,
};

/** Hệ số loại gói — đồng bộ BE TYPE_FACTORS. */
export const TYPE_FACTORS = {
  STANDARD: 1,
  ENHANCED: 1.25,
  COMPREHENSIVE: 1.5,
};

/** Hệ số kỳ hạn — đồng bộ BE TERM_FACTORS. */
export const TERM_FACTORS = {
  12: 1,
  24: 1.85,
  36: 2.65,
};

/** Phí tối thiểu mỗi coverage — đồng bộ BE MINIMUM_PREMIUM. */
export const MIN_PREMIUM = 100000;

export function estimatePremium({ limit, baseRate, termMonths = 12, coverageType, deductible = 0 } = {}) {
  const L = Number(limit);
  const rate = Number(baseRate);
  const D = Number(deductible) || 0;
  if (!Number.isFinite(L) || L <= 0 || !Number.isFinite(rate) || rate <= 0) return 0;

  const rawTerm = Number(termMonths);
  const T = Number.isFinite(rawTerm) && rawTerm > 0 ? rawTerm : 12;
  const termFactor = TERM_FACTORS[T] ?? T / 12;

  const typeKey = String(coverageType ?? 'STANDARD').toUpperCase();
  const typeFactor = TYPE_FACTORS[typeKey] ?? 1;

  let deductibleAdj = 1;
  if (D > 0 && L > 0) {
    const ratio = D / L;
    deductibleAdj = 1 - 0.35 * (1 - Math.exp(-8 * ratio));
  }

  const premium = (L * rate / 100) * termFactor * typeFactor * deductibleAdj;
  return Math.round(Math.max(MIN_PREMIUM, premium) * 100) / 100;
}
