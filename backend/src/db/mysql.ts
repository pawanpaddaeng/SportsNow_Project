import mysql from 'mysql2/promise';
import { env } from '../config/env';

// One pool for the whole app; mysql2 reuses connections from it.
export const pool = mysql.createPool(env.mysqlUrl);

// values that can fill a query's ? placeholders
export type SqlValue = string | number | boolean | Date | null;

// the pool, or one of its connections when the query is part of a transaction
export interface Queryable {
  execute(sql: string, params: SqlValue[]): Promise<unknown>;
}

// what mysql2 reports for an INSERT, UPDATE or DELETE
export interface WriteResult {
  insertId: number;
  affectedRows: number;
  // rows whose values actually changed, 0 when an UPDATE set the values they already had
  changedRows: number;
}

/*  runs a SELECT and returns its rows typed as T. The rows are typed here once so
    the queries don't depend on mysql2's result types
 */
export async function selectRows<T>(sql: string, params: SqlValue[], db: Queryable = pool): Promise<T[]> {
  const [rows] = (await db.execute(sql, params)) as unknown as [T[], unknown];
  return rows;
}

// runs an INSERT, UPDATE or DELETE and returns what it changed
export async function executeWrite(sql: string, params: SqlValue[], db: Queryable = pool): Promise<WriteResult> {
  const [result] = (await db.execute(sql, params)) as unknown as [WriteResult, unknown];
  return result;
}
