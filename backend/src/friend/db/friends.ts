import { executeWrite, inTransaction, type Queryable, selectRows } from '../../core/db/mysql';
import { matchName } from '../search/nameMatch';

// the only details of another user the friend screens show
export interface FriendUser {
  userId: number;
  firstName: string;
  lastName: string;
}

interface FriendUserRow {
  user_id: number;
  first_name: string;
  last_name: string;
}

function toFriendUser(row: FriendUserRow): FriendUser {
  return { userId: row.user_id, firstName: row.first_name, lastName: row.last_name };
}

// most people a search returns
const MAX_SEARCH_RESULTS = 20;

// how a search result is connected to the user searching
export type FriendshipStatus = 'none' | 'friends' | 'request_sent' | 'request_received';

export interface UserSearchResult extends FriendUser {
  friendshipStatus: FriendshipStatus;
}

interface FriendshipRow {
  is_friend: number;
  request_sent: number;
  request_received: number;
}

interface UserSearchRow extends FriendUserRow, FriendshipRow {}

function toFriendshipStatus(row: FriendshipRow): FriendshipStatus {
  if (row.is_friend) return 'friends';
  if (row.request_sent) return 'request_sent';
  if (row.request_received) return 'request_received';
  return 'none';
}

function toUserSearchResult(row: UserSearchRow): UserSearchResult {
  return { ...toFriendUser(row), friendshipStatus: toFriendshipStatus(row) };
}

// how otherUserId is connected to userId, e.g. 'request_sent' when userId asked them
export async function getFriendshipStatus(userId: number, otherUserId: number): Promise<FriendshipStatus> {
  const rows = await selectRows<FriendshipRow>(
    `SELECT EXISTS (SELECT 1 FROM friendships WHERE user_id = ? AND friend_id = ?) AS is_friend,
            EXISTS (SELECT 1 FROM friend_requests
                    WHERE sender_id = ? AND receiver_id = ? AND status = 'pending') AS request_sent,
            EXISTS (SELECT 1 FROM friend_requests
                    WHERE sender_id = ? AND receiver_id = ? AND status = 'pending') AS request_received`,
    [userId, otherUserId, userId, otherUserId, otherUserId, userId],
  );
  const row = rows[0];
  return row ? toFriendshipStatus(row) : 'none';
}

/*  users whose name matches or is similar to text, best match first, except the user
    searching. MySQL can't compare how similar names are, so every name is compared here
 */
export async function searchUsers(text: string, userId: number): Promise<UserSearchResult[]> {
  const rows = await selectRows<UserSearchRow>(
    `SELECT u.user_id, u.first_name, u.last_name,
            EXISTS (SELECT 1 FROM friendships f WHERE f.user_id = ? AND f.friend_id = u.user_id) AS is_friend,
            EXISTS (SELECT 1 FROM friend_requests r
                    WHERE r.sender_id = ? AND r.receiver_id = u.user_id AND r.status = 'pending') AS request_sent,
            EXISTS (SELECT 1 FROM friend_requests r
                    WHERE r.sender_id = u.user_id AND r.receiver_id = ? AND r.status = 'pending') AS request_received
     FROM users u
     WHERE u.user_id <> ?`,
    [userId, userId, userId, userId],
  );
  const matches = rows.flatMap((row) => {
    const user = toUserSearchResult(row);
    const match = matchName(text, `${user.firstName} ${user.lastName}`);
    return match ? [{ user, match }] : [];
  });
  matches.sort((a, b) =>
    a.match.tier - b.match.tier
    || a.match.difference - b.match.difference
    || `${a.user.firstName} ${a.user.lastName}`.localeCompare(`${b.user.firstName} ${b.user.lastName}`)
    || a.user.userId - b.user.userId);
  return matches.slice(0, MAX_SEARCH_RESULTS).map((found) => found.user);
}

// the user's friends in name order
export async function getFriends(userId: number): Promise<FriendUser[]> {
  const rows = await selectRows<FriendUserRow>(
    `SELECT u.user_id, u.first_name, u.last_name
     FROM friendships f
     JOIN users u ON u.user_id = f.friend_id
     WHERE f.user_id = ?
     ORDER BY u.first_name, u.last_name, u.user_id`,
    [userId],
  );
  return rows.map(toFriendUser);
}

// whether the two users are friends, used by Game before an organizer invites someone
export async function areFriends(userId: number, otherUserId: number): Promise<boolean> {
  const rows = await selectRows<{ user_id: number }>(
    'SELECT user_id FROM friendships WHERE user_id = ? AND friend_id = ?',
    [userId, otherUserId],
  );
  return rows.length > 0;
}

// a request waiting for the user's answer
export interface IncomingFriendRequest {
  requestId: number;
  sender: FriendUser;
}

// requests sent to the user that are still waiting for an answer, newest first
export async function getIncomingFriendRequests(userId: number): Promise<IncomingFriendRequest[]> {
  const rows = await selectRows<FriendUserRow & { request_id: number }>(
    `SELECT r.request_id, u.user_id, u.first_name, u.last_name
     FROM friend_requests r
     JOIN users u ON u.user_id = r.sender_id
     WHERE r.receiver_id = ? AND r.status = 'pending'
     ORDER BY r.created_at DESC, r.request_id DESC`,
    [userId],
  );
  return rows.map((row) => ({ requestId: row.request_id, sender: toFriendUser(row) }));
}

/*  locks both users' rows, lowest id first, so everything changing the requests between
    two users happens one at a time, and two people asking each other at once can't both
    succeed. Returns how many of the users exist
 */
async function lockUsers(connection: Queryable, userId: number, otherUserId: number): Promise<number> {
  const rows = await selectRows<{ user_id: number }>(
    'SELECT user_id FROM users WHERE user_id IN (?, ?) ORDER BY user_id FOR UPDATE',
    [userId, otherUserId],
    connection,
  );
  return rows.length;
}

// why a user can't send a friend request
export type FriendRequestProblem =
  | 'own_request'
  | 'user_not_found'
  | 'already_friends'
  | 'already_requested'
  | 'request_received';

export type SendFriendRequestResult =
  | { ok: true; requestId: number }
  | { ok: false; problem: FriendRequestProblem };

interface FriendRequestRow {
  request_id: number;
  sender_id: number;
  receiver_id: number;
  status: 'pending' | 'accepted' | 'declined';
}

/*  sends a friend request from senderId to receiverId. Blocked for yourself, a missing
    user, someone who is already a friend, and when either of the two already has a
    pending request to the other
 */
export async function sendFriendRequest(senderId: number, receiverId: number): Promise<SendFriendRequestResult> {
  if (senderId === receiverId) return { ok: false, problem: 'own_request' };

  return inTransaction(async (connection) => {
    if (await lockUsers(connection, senderId, receiverId) < 2) return { ok: false, problem: 'user_not_found' };

    const friendships = await selectRows<{ user_id: number }>(
      'SELECT user_id FROM friendships WHERE user_id = ? AND friend_id = ?',
      [senderId, receiverId],
      connection,
    );
    if (friendships.length > 0) return { ok: false, problem: 'already_friends' };

    const requests = await selectRows<FriendRequestRow>(
      `SELECT request_id, sender_id, receiver_id, status
       FROM friend_requests
       WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)`,
      [senderId, receiverId, receiverId, senderId],
      connection,
    );
    const pending = requests.find((request) => request.status === 'pending');
    if (pending) {
      return { ok: false, problem: pending.sender_id === senderId ? 'already_requested' : 'request_received' };
    }

    const previous = requests.find((request) => request.sender_id === senderId);
    if (previous) {
      await executeWrite(
        `UPDATE friend_requests
         SET status = 'pending', created_at = CURRENT_TIMESTAMP, responded_at = NULL
         WHERE request_id = ?`,
        [previous.request_id],
        connection,
      );
      return { ok: true, requestId: previous.request_id };
    }

    const result = await executeWrite(
      'INSERT INTO friend_requests (sender_id, receiver_id) VALUES (?, ?)',
      [senderId, receiverId],
      connection,
    );
    return { ok: true, requestId: result.insertId };
  });
}

// why a user can't answer a friend request
export type FriendDecisionProblem = 'request_not_found' | 'already_responded';

export type DecideFriendRequestResult =
  // senderId so the sender can be notified
  | { ok: true; senderId: number }
  | { ok: false; problem: FriendDecisionProblem };

const SELECT_FRIEND_REQUEST = 'SELECT request_id, sender_id, receiver_id, status FROM friend_requests WHERE request_id = ?';

/*  the receiver accepts or declines a pending request, accepting makes the two friends.
    A request sent to someone else counts as not found, so its id reveals nothing
 */
export async function decideFriendRequest(
  requestId: number,
  receiverId: number,
  accept: boolean,
): Promise<DecideFriendRequestResult> {
  return inTransaction(async (connection) => {
    const found = (await selectRows<FriendRequestRow>(SELECT_FRIEND_REQUEST, [requestId], connection))[0];
    if (found?.receiver_id !== receiverId) return { ok: false, problem: 'request_not_found' };

    // read again once locked, it may have been answered while waiting for the lock
    await lockUsers(connection, found.sender_id, receiverId);
    const request = (await selectRows<FriendRequestRow>(`${SELECT_FRIEND_REQUEST} FOR UPDATE`, [requestId], connection))[0];
    if (!request) return { ok: false, problem: 'request_not_found' };
    if (request.status !== 'pending') return { ok: false, problem: 'already_responded' };

    if (accept) {
      // IGNORE: if they somehow became friends already, accepting just closes the request
      await executeWrite(
        'INSERT IGNORE INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)',
        [request.sender_id, receiverId, receiverId, request.sender_id],
        connection,
      );
    }
    await executeWrite(
      'UPDATE friend_requests SET status = ?, responded_at = CURRENT_TIMESTAMP WHERE request_id = ?',
      [accept ? 'accepted' : 'declined', requestId],
      connection,
    );
    return { ok: true, senderId: request.sender_id };
  });
}
