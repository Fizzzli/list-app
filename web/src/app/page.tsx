'use client';

import { useEffect, useState } from 'react';
import { useAppStore } from '@/lib/store';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';

export default function Home() {
  const { user, lists, isLoading, createList, deleteList, refreshLists } = useAppStore();
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [newListTitle, setNewListTitle] = useState('');

  useEffect(() => {
    useAppStore.getState().init();
  }, []);

  const handleCreateList = async () => {
    if (!newListTitle.trim()) return;
    
    await createList({
      title: newListTitle,
      templateId: 'generic',
      items: [],
      visibility: 'private',
    });
    
    setNewListTitle('');
    setIsCreateDialogOpen(false);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p>加载中...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <h1 className="text-xl font-bold">ListApp</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-muted-foreground">
              本地模式 · 数据保存在浏览器
            </span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-8">
          <h2 className="text-2xl font-semibold">我的列表</h2>
          
          <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
            <DialogTrigger asChild>
              <Button>创建列表</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>创建新列表</DialogTitle>
                <DialogDescription>
                  给你的列表起个名字
                </DialogDescription>
              </DialogHeader>
              <Input
                value={newListTitle}
                onChange={(e) => setNewListTitle(e.target.value)}
                placeholder="例如：想看过的电影、想去的餐厅..."
                onKeyDown={(e) => e.key === 'Enter' && handleCreateList()}
              />
              <DialogFooter>
                <Button onClick={handleCreateList}>创建</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>

        {/* Lists Grid */}
        {lists.length === 0 ? (
          <div className="text-center py-12 text-muted-foreground">
            <p>暂无列表</p>
            <p className="text-sm mt-2">点击上方"创建列表"开始</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {lists.map((list) => (
              <Card key={list.id} className="cursor-pointer hover:shadow-md transition-shadow">
                <CardHeader>
                  <CardTitle>{list.title}</CardTitle>
                  <CardDescription>
                    {list.items.length} 个条目 · {list.visibility === 'public' ? '公开' : '私有'}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" className="flex-1">
                      查看
                    </Button>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => list.id && deleteList(list.id)}
                    >
                      删除
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
