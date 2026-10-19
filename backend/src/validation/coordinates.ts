// a point on the map in degrees
export interface Coordinates {
  latitude: number;
  longitude: number;
}

// a query string value as a number, NaN if it's missing, repeated or not a number
function queryNumber(value: unknown): number {
  return typeof value === 'string' && value.trim() !== '' ? Number(value) : NaN;
}

/*  latitude and longitude from a query string such as ?lat=49.26&lng=-123.25,
    or null if either is missing, not a number or out of range
 */
export function parseCoordinates(lat: unknown, lng: unknown): Coordinates | null {
  const latitude = queryNumber(lat);
  const longitude = queryNumber(lng);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) return null;
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) return null;
  return { latitude, longitude };
}
