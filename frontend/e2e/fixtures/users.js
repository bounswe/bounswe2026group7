import { faker } from '@faker-js/faker/locale/tr';

/**
 * Faker-driven user factory for the AT-01 register flow. The password matches
 * AuthService's @ValidPassword rule (>=8 chars, upper+lower+digit) so the
 * register POST does not 400 on validation alone.
 */
export function makeUser({ isMentor = false } = {}) {
  const firstName = faker.person.firstName();
  const lastName = faker.person.lastName();
  const email = `e2e-${Date.now()}-${faker.string.alphanumeric(6).toLowerCase()}@example.com`;
  const password = `P@ss${faker.number.int({ min: 100000, max: 999999 })}`;
  return { firstName, lastName, email, password, isMentor };
}

export function newPassword() {
  return `Reset${faker.number.int({ min: 100000, max: 999999 })}!`;
}
