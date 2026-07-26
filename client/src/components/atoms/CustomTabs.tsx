import React from 'react';
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from '@/components/ui/tabs';
import { cn } from '@/lib/utils';

export interface CustomTabItem {
  key: string;
  label: React.ReactNode;
  children?: React.ReactNode;
  disabled?: boolean;
}

export interface CustomTabsProps {
  items?: CustomTabItem[];
  activeKey?: string;
  defaultActiveKey?: string;
  onChange?: (key: string) => void;
  className?: string;
}

const CustomTabs: React.FC<CustomTabsProps> = ({
  items = [],
  activeKey,
  defaultActiveKey,
  onChange,
  className,
}) => (
  <Tabs
    value={activeKey}
    defaultValue={defaultActiveKey ?? items[0]?.key}
    onValueChange={onChange}
    className={cn('w-full', className)}
  >
    <TabsList>
      {items.map((item) => (
        <TabsTrigger key={item.key} value={item.key} disabled={item.disabled}>
          {item.label}
        </TabsTrigger>
      ))}
    </TabsList>
    {items.map((item) => (
      <TabsContent key={item.key} value={item.key}>
        {item.children}
      </TabsContent>
    ))}
  </Tabs>
);

export default CustomTabs;
