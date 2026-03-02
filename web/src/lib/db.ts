// Dexie.js - IndexedDB 封装
import Dexie, { Table } from 'dexie';

// 数据类型
export interface ListItem {
  id?: string;
  name: string;
  status: 'todo' | 'done';
  fields?: Record<string, any>;
  version: number;
  lastModified: number;
}

export interface UserList {
  id?: string;
  title: string;
  templateId: string;
  items: ListItem[];
  visibility: 'private' | 'public';
  shareToken?: string;
  ownerId: string;
  version: number;
  lastModified: number;
}

// 数据库定义
class ListAppDB extends Dexie {
  lists!: Table<UserList, string>;

  constructor() {
    super('ListAppDB');
    this.version(1).stores({
      lists: 'id, title, templateId, visibility, ownerId, lastModified',
    });
  }
}

// 单例
export const db = new ListAppDB();

// 辅助函数
export async function getAllLists(): Promise<UserList[]> {
  return await db.lists.toArray();
}

export async function getList(id: string): Promise<UserList | undefined> {
  return await db.lists.get(id);
}

export async function createList(list: Omit<UserList, 'id' | 'version' | 'lastModified'>): Promise<string> {
  const newList: UserList = {
    ...list,
    version: 1,
    lastModified: Date.now(),
  };
  const id = await db.lists.add(newList);
  return id as string;
}

export async function updateList(id: string, updates: Partial<UserList>): Promise<void> {
  await db.lists.update(id, {
    ...updates,
    version: Date.now(),
    lastModified: Date.now(),
  });
}

export async function deleteList(id: string): Promise<void> {
  await db.lists.delete(id);
}

export async function addItem(listId: string, item: Omit<ListItem, 'id' | 'version' | 'lastModified'>): Promise<void> {
  const list = await getList(listId);
  if (!list) throw new Error('List not found');

  const newItem: ListItem = {
    ...item,
    version: 1,
    lastModified: Date.now(),
  };

  await updateList(listId, {
    items: [...list.items, newItem],
  });
}

export async function updateItem(listId: string, itemId: string, updates: Partial<ListItem>): Promise<void> {
  const list = await getList(listId);
  if (!list) throw new Error('List not found');

  const updatedItems = list.items.map(item =>
    item.id === itemId
      ? { ...item, ...updates, version: Date.now(), lastModified: Date.now() }
      : item
  );

  await updateList(listId, { items: updatedItems });
}

export async function deleteItem(listId: string, itemId: string): Promise<void> {
  const list = await getList(listId);
  if (!list) throw new Error('List not found');

  const updatedItems = list.items.filter(item => item.id !== itemId);
  await updateList(listId, { items: updatedItems });
}
