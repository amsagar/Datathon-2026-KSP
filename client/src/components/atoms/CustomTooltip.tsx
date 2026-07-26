import React from 'react';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';

export type CustomTooltipPlacement =
  | 'top'
  | 'left'
  | 'right'
  | 'bottom'
  | 'topLeft'
  | 'topRight'
  | 'bottomLeft'
  | 'bottomRight'
  | 'leftTop'
  | 'leftBottom'
  | 'rightTop'
  | 'rightBottom';

export interface CustomTooltipProps {
  title: React.ReactNode;
  children: React.ReactNode;
  placement?: CustomTooltipPlacement;
  disabled?: boolean;
  mouseEnterDelay?: number;
}

type Side = 'top' | 'right' | 'bottom' | 'left';
type Align = 'start' | 'center' | 'end';

const placementMap: Record<
  CustomTooltipPlacement,
  { side: Side; align: Align }
> = {
  top: { side: 'top', align: 'center' },
  bottom: { side: 'bottom', align: 'center' },
  left: { side: 'left', align: 'center' },
  right: { side: 'right', align: 'center' },
  topLeft: { side: 'top', align: 'start' },
  topRight: { side: 'top', align: 'end' },
  bottomLeft: { side: 'bottom', align: 'start' },
  bottomRight: { side: 'bottom', align: 'end' },
  leftTop: { side: 'left', align: 'start' },
  leftBottom: { side: 'left', align: 'end' },
  rightTop: { side: 'right', align: 'start' },
  rightBottom: { side: 'right', align: 'end' },
};

const CustomTooltip: React.FC<CustomTooltipProps> = ({
  title,
  children,
  placement = 'top',
  disabled = false,
  mouseEnterDelay = 0.25,
}) => {
  if (disabled || !title) return <>{children}</>;

  const { side, align } = placementMap[placement] ?? placementMap.top;

  return (
    <TooltipProvider delayDuration={Math.round(mouseEnterDelay * 1000)}>
      <Tooltip>
        <TooltipTrigger asChild>
          {React.isValidElement(children) ? (
            children
          ) : (
            <span className="inline-flex max-w-full">{children}</span>
          )}
        </TooltipTrigger>
        <TooltipContent side={side} align={align} className="max-w-xs text-left">
          {title}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

export default CustomTooltip;
