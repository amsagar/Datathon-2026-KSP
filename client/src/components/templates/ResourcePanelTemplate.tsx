import React from 'react';
import CustomButton from '@atoms/CustomButton';
import CustomIcon from '@atoms/CustomIcon';
import CustomTooltip from '@atoms/CustomTooltip';
import { confirm } from '@atoms/CustomConfirm';
import * as styles from '@styles/resourcePanel.module.scss';

export interface ResourceListItem {
  id: string;
  name: string;
  meta?: React.ReactNode;
}

export interface ResourcePanelTemplateProps<T extends ResourceListItem> {
  title: string;
  subtitle?: string;
  items: T[];
  selectedId: string | null;
  onSelect: (item: T) => void;
  onNew: () => void;
  onDelete?: (item: T) => void | Promise<void>;
  deleteConfirmTitle?: (item: T) => string;
  deleteConfirmBody?: (item: T) => React.ReactNode;
  scopeBar?: React.ReactNode;
  newLabel?: string;
  emptyListLabel?: string;
  formPlaceholder?: React.ReactNode;
  children: React.ReactNode;
}

function ResourcePanelTemplate<T extends ResourceListItem>({
  title,
  subtitle,
  items,
  selectedId,
  onSelect,
  onNew,
  onDelete,
  deleteConfirmTitle,
  deleteConfirmBody,
  scopeBar,
  newLabel = '+ New',
  emptyListLabel = 'Nothing yet',
  formPlaceholder = 'Select an item to edit, or create a new one.',
  children,
}: ResourcePanelTemplateProps<T>) {
  const askDelete = (item: T) => {
    if (!onDelete) return;
    confirm({
      title: deleteConfirmTitle ? deleteConfirmTitle(item) : `Delete "${item.name}"?`,
      body:
        deleteConfirmBody?.(item) ??
        "This can't be undone.",
      danger: true,
      okText: 'Delete',
      onOk: () => onDelete(item),
    });
  };

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <div>
          <h2 className={styles.title}>{title}</h2>
          {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
        </div>
      </div>

      {scopeBar && <div className={styles.scopeBar}>{scopeBar}</div>}

      <div className={styles.body}>
        <div className={styles.list}>
          <CustomButton variant="primary" fullWidth onClick={onNew}>
            {newLabel}
          </CustomButton>
          {items.length === 0 && (
            <div className={styles.placeholder}>{emptyListLabel}</div>
          )}
          {items.map((item) => (
            <div
              key={item.id}
              className={`${styles.row} ${
                selectedId === item.id ? styles.rowSelected : ''
              }`}
              onClick={() => onSelect(item)}
            >
              <div className={styles.rowMain}>
                <div className={styles.rowName}>{item.name}</div>
                {item.meta && <div className={styles.rowMeta}>{item.meta}</div>}
              </div>
              {onDelete && (
                <CustomTooltip title="Delete">
                  <CustomButton
                    variant="text"
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      askDelete(item);
                    }}
                    aria-label="Delete"
                  >
                    <CustomIcon name="delete" />
                  </CustomButton>
                </CustomTooltip>
              )}
            </div>
          ))}
        </div>

        <div className={styles.form}>
          {selectedId == null ? (
            <div className={styles.placeholder}>{formPlaceholder}</div>
          ) : (
            children
          )}
        </div>
      </div>
    </div>
  );
}

export default ResourcePanelTemplate;
