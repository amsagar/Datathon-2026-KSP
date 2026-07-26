import React from 'react';
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar';
import { cn } from '@/lib/utils';

export type CustomAvatarSize = number | 'small' | 'default' | 'large';

export interface CustomAvatarProps {
  src?: string;
  size?: CustomAvatarSize;
  shape?: 'circle' | 'square';
  icon?: React.ReactNode;
  children?: React.ReactNode;
  style?: React.CSSProperties;
  className?: string;
  alt?: string;
}

const namedSize: Record<'small' | 'default' | 'large', number> = {
  small: 24,
  default: 32,
  large: 40,
};

const CustomAvatar: React.FC<CustomAvatarProps> = ({
  src,
  size = 'default',
  shape = 'circle',
  icon,
  children,
  style,
  className,
  alt,
}) => {
  const px = typeof size === 'number' ? size : namedSize[size];
  const sizeStyle: React.CSSProperties = { width: px, height: px };
  const shapeClass = shape === 'square' ? 'rounded-md' : 'rounded-full';

  const fallbackContent = children ?? icon;

  return (
    <Avatar
      className={cn(shapeClass, className)}
      style={{ ...sizeStyle, ...style }}
    >
      {src ? <AvatarImage src={src} alt={alt} className={shapeClass} /> : null}
      <AvatarFallback
        className={cn(
          'bg-primary text-primary-foreground font-medium',
          shapeClass,
        )}
        style={style}
      >
        {fallbackContent}
      </AvatarFallback>
    </Avatar>
  );
};

export default CustomAvatar;
