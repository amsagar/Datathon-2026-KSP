import * as React from 'react';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';

import { cn } from '@/lib/utils';

export type CalendarProps = React.ComponentProps<typeof DayPicker>;

/**
 * react-day-picker v10 scopes CSS variables on `.rdp-root` itself (defaults to
 * blue). Pass tokens on the DayPicker root so KSP crimson wins.
 */
function Calendar({ className, style, ...props }: CalendarProps) {
  return (
    <DayPicker
      className={cn('rdp-ksp', className)}
      style={
        {
          '--rdp-accent-color': 'var(--primary)',
          '--rdp-accent-background-color': 'color-mix(in srgb, var(--primary) 14%, transparent)',
          '--rdp-today-color': 'var(--primary)',
          '--rdp-day-width': '2.25rem',
          '--rdp-day-height': '2.25rem',
          '--rdp-day_button-width': '2.1rem',
          '--rdp-day_button-height': '2.1rem',
          '--rdp-day_button-border-radius': '0.375rem',
          '--rdp-selected-border': '2px solid var(--primary)',
          '--rdp-range_start-color': 'var(--primary-foreground)',
          '--rdp-range_end-color': 'var(--primary-foreground)',
          '--rdp-range_start-date-background-color': 'var(--primary)',
          '--rdp-range_end-date-background-color': 'var(--primary)',
          '--rdp-range_middle-background-color':
            'color-mix(in srgb, var(--primary) 14%, transparent)',
          '--rdp-range_middle-color': 'var(--foreground)',
          '--rdp-months-gap': '1.25rem',
          color: 'var(--foreground)',
          fontFamily: 'inherit',
          ...style,
        } as React.CSSProperties
      }
      {...props}
    />
  );
}

export { Calendar };
