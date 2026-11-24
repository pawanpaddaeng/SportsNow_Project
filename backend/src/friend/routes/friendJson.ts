import type { FriendUser } from '../db/friends';

// a user as the friends endpoints send them to the app
export function friendUserJson(user: FriendUser) {
  return { user_id: user.userId, first_name: user.firstName, last_name: user.lastName };
}
