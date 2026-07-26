import React from 'react';
import {
  Plus,
  Trash2,
  Pencil,
  X,
  Check,
  Send,
  RotateCw,
  Settings,
  Wrench,
  Key,
  Lightbulb,
  FileText,
  Highlighter,
  Plug,
  PlayCircle,
  Inbox,
  Bot,
  ChevronDown,
  ChevronRight,
  Loader2,
  TriangleAlert,
  CircleCheck,
  CircleX,
  Undo2,
  Save,
  Upload,
  Download,
  Search,
  MoreHorizontal,
  ArrowUp,
  ArrowLeft,
  Square,
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Info,
  Copy,
  ThumbsUp,
  ThumbsDown,
  LogOut,
  Palette,
  BarChart3,
  Star,
  Users,
  ScrollText,
  CircleUser,
  ShieldCheck,
  History,
  type LucideIcon,
} from 'lucide-react';

export type CustomIconName =
  | 'plus'
  | 'delete'
  | 'edit'
  | 'close'
  | 'check'
  | 'send'
  | 'reload'
  | 'settings'
  | 'tool'
  | 'key'
  | 'skill'
  | 'document'
  | 'style'
  | 'mcp'
  | 'play'
  | 'inbox'
  | 'robot'
  | 'caret-down'
  | 'caret-right'
  | 'loading'
  | 'warning'
  | 'check-circle'
  | 'close-circle'
  | 'undo'
  | 'save'
  | 'upload'
  | 'download'
  | 'search'
  | 'more'
  | 'arrowUp'
  | 'arrowLeft'
  | 'stop'
  | 'message'
  | 'sidebarFold'
  | 'sidebarUnfold'
  | 'info'
  | 'copy'
  | 'like'
  | 'dislike'
  | 'logout'
  | 'appearance'
  | 'usage'
  | 'star'
  | 'star-filled'
  | 'users'
  | 'audit'
  | 'profile'
  | 'shield'
  | 'history';

const iconMap: Record<CustomIconName, LucideIcon> = {
  plus: Plus,
  delete: Trash2,
  edit: Pencil,
  close: X,
  check: Check,
  send: Send,
  reload: RotateCw,
  settings: Settings,
  tool: Wrench,
  key: Key,
  skill: Lightbulb,
  document: FileText,
  style: Highlighter,
  mcp: Plug,
  play: PlayCircle,
  inbox: Inbox,
  robot: Bot,
  'caret-down': ChevronDown,
  'caret-right': ChevronRight,
  loading: Loader2,
  warning: TriangleAlert,
  'check-circle': CircleCheck,
  'close-circle': CircleX,
  undo: Undo2,
  save: Save,
  upload: Upload,
  download: Download,
  search: Search,
  more: MoreHorizontal,
  arrowUp: ArrowUp,
  arrowLeft: ArrowLeft,
  stop: Square,
  message: MessageSquare,
  sidebarFold: PanelLeftClose,
  sidebarUnfold: PanelLeftOpen,
  info: Info,
  copy: Copy,
  like: ThumbsUp,
  dislike: ThumbsDown,
  logout: LogOut,
  appearance: Palette,
  usage: BarChart3,
  star: Star,
  'star-filled': Star,
  users: Users,
  audit: ScrollText,
  profile: CircleUser,
  shield: ShieldCheck,
  history: History,
};

export interface CustomIconProps {
  name: CustomIconName;
  size?: number;
  color?: string;
  className?: string;
}

const CustomIcon: React.FC<CustomIconProps> = ({
  name,
  size = 14,
  color,
  className,
}) => {
  const Comp = iconMap[name];
  return (
    <Comp
      className={className}
      size={size}
      color={color}
      // Preserve antd's filled-star look for the 'star-filled' name.
      fill={name === 'star-filled' ? 'currentColor' : 'none'}
    />
  );
};

export default CustomIcon;
