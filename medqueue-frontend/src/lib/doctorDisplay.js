export function formatDoctorName(name) {
  const withoutTitles = String(name ?? '').trim().replace(/^(?:dr\.?\s*)+/i, '').trim()
  return withoutTitles ? `Dr. ${withoutTitles}` : 'Dr.'
}
