// 全局状态管理 (Zustand) - 纯本地版本
import { create } from 'zustand';
import { getAllLists, createList, updateList, deleteList, addItem, updateItem, deleteItem, type UserList, type ListItem } from './db';

export type { UserList, ListItem };

export interface User {
  uid: string;
  isAnonymous: boolean;
  email?: string | null;
}

// 生成一个简单的用户 ID
function generateUserId(): string {
  let savedId = localStorage.getItem('listapp_user_id');
  if (!savedId) {
    savedId = 'user_' + Math.random().toString(36).slice(2);
    localStorage.setItem('listapp_user_id', savedId);
  }
  return savedId;
}

// 状态定义
interface AppState {
  user: User | null;
  lists: UserList[];
  isLoading: boolean;
  error: string | null;
  
  // Actions
  init: () => Promise<void>;
  createList: (list: Omit<UserList, 'id' | 'version' | 'lastModified' | 'ownerId'>) => Promise<void>;
  updateList: (id: string, updates: Partial<UserList>) => Promise<void>;
  deleteList: (id: string) => Promise<void>;
  addItem: (listId: string, item: Omit<ListItem, 'id' | 'version' | 'lastModified'>) => Promise<void>;
  updateItem: (listId: string, itemId: string, updates: Partial<ListItem>) => Promise<void>;
  deleteItem: (listId: string, itemId: string) => Promise<void>;
  refreshLists: () => Promise<void>;
}

// 创建 store
export const useAppStore = create<AppState>((set, get) => ({
  user: null,
  lists: [],
  isLoading: true,
  error: null,

  init: async () => {
    try {
      const userId = generateUserId();
      set({ 
        user: { 
          uid: userId, 
          isAnonymous: true,
          email: null,
        },
        isLoading: false,
      });
      await get().refreshLists();
    } catch (error: any) {
      set({ error: error.message, isLoading: false });
    }
  },

  refreshLists: async () => {
    try {
      const lists = await getAllLists();
      set({ lists, isLoading: false });
    } catch (error: any) {
      set({ error: error.message });
    }
  },

  createList: async (list) => {
    const { user } = get();
    if (!user) throw new Error('Not initialized');

    const id = await createList({
      ...list,
      ownerId: user.uid,
    });
    
    await get().refreshLists();
  },

  updateList: async (id, updates) => {
    await updateList(id, updates);
    await get().refreshLists();
  },

  deleteList: async (id) => {
    await deleteList(id);
    await get().refreshLists();
  },

  addItem: async (listId, item) => {
    await addItem(listId, item);
    await get().refreshLists();
  },

  updateItem: async (listId, itemId, updates) => {
    await updateItem(listId, itemId, updates);
    await get().refreshLists();
  },

  deleteItem: async (listId, itemId) => {
    await deleteItem(listId, itemId);
    await get().refreshLists();
  },
}));
