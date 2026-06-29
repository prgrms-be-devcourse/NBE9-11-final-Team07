const MOBILE_PHONE_PATTERN = /^(?:010\d{8}|010-\d{4}-\d{4})$/

function getPhoneDigits(phone: string): string {
  return phone.replace(/\D/g, '')
}

export function isValidPhoneNumber(phone: string): boolean {
  return MOBILE_PHONE_PATTERN.test(phone.trim())
}

export function normalizePhoneNumber(phone: string): string {
  const digits = getPhoneDigits(phone)
  return digits.replace(/^(\d{3})(\d{4})(\d{4})$/, '$1-$2-$3')
}
