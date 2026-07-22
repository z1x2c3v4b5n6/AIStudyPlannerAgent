const BUSINESS_TIME_ZONE = 'Asia/Shanghai'

function parts(date: Date) {
  const values = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date)
  return Object.fromEntries(values.map((item) => [item.type, item.value]))
}

export function shanghaiDateTime(date = new Date()) {
  const value = parts(date)
  return `${value.year}-${value.month}-${value.day}T${value.hour}:${value.minute}:${value.second}`
}

export function shanghaiDate(date = new Date()) {
  return shanghaiDateTime(date).slice(0, 10)
}

export function wallTimeMillis(value: string) {
  const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})$/)
  if (!matched) return Number.NaN
  const [, year, month, day, hour, minute, second] = matched.map(Number)
  return Date.UTC(year, month - 1, day, hour, minute, second)
}

export function addBusinessDays(date: string, days: number) {
  const [year, month, day] = date.split('-').map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + days))
  return shifted.toISOString().slice(0, 10)
}

export function inclusiveBusinessDays(start: string, end: string) {
  return Math.round((wallTimeMillis(`${end}T00:00:00`) - wallTimeMillis(`${start}T00:00:00`)) / 86_400_000) + 1
}

export function addWallMinutes(value: string, minutes: number) {
  const date = new Date(wallTimeMillis(value) + minutes * 60_000)
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`
}
