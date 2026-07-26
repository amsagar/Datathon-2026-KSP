import React, { createContext, useContext } from 'react';
import { toast } from 'sonner';

export type NotificationTitle = 'Success' | 'Error' | 'Warning' | 'Info';

type OpenNotification = (message: string, title: NotificationTitle) => void;

/** Backed by sonner. Keeps the (message, title) signature all callers use. */
const openNotification: OpenNotification = (message, title) => {
  switch (title) {
    case 'Success':
      toast.success(title, { description: message });
      break;
    case 'Error':
      toast.error(title, { description: message });
      break;
    case 'Warning':
      toast.warning(title, { description: message });
      break;
    default:
      toast.info(title, { description: message });
  }
};

const NotificationContext = createContext<OpenNotification>(openNotification);

export const useNotification = (): OpenNotification =>
  useContext(NotificationContext);

interface NotificationProviderProps {
  children: React.ReactNode;
}

export const NotificationProvider: React.FC<NotificationProviderProps> = ({
  children,
}) => (
  <NotificationContext.Provider value={openNotification}>
    {children}
  </NotificationContext.Provider>
);
