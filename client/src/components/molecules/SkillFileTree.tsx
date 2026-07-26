import React, { useMemo } from 'react';
import CustomIcon from '@atoms/CustomIcon';
import type { SkillFileNode } from '@interfaces/skill.interface';
import * as styles from '@styles/skillsPage.module.scss';

export interface SkillFileTreeProps {
  nodes: SkillFileNode[];
  selectedPath: string | null;
  expanded: Set<string>;
  onToggleFolder: (folderPath: string) => void;
  onSelectFile: (path: string) => void;
}

const isFolderNode = (node: SkillFileNode): boolean =>
  node.type === 'folder' || node.children.length > 0;

const sortNodes = (nodes: SkillFileNode[]): SkillFileNode[] =>
  [...nodes]
    .sort((a, b) => {
      if (a.name === 'SKILL.md') return -1;
      if (b.name === 'SKILL.md') return 1;
      const aFolder = isFolderNode(a);
      const bFolder = isFolderNode(b);
      if (aFolder !== bFolder) return aFolder ? -1 : 1;
      return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
    })
    .map((node) =>
      isFolderNode(node)
        ? { ...node, children: sortNodes(node.children) }
        : node
    );

const splitFileName = (name: string): { stem: string; ext: string } => {
  const dot = name.lastIndexOf('.');
  if (dot <= 0) return { stem: name, ext: '' };
  return { stem: name.slice(0, dot), ext: name.slice(dot) };
};

const SkillFileTree: React.FC<SkillFileTreeProps> = ({
  nodes,
  selectedPath,
  expanded,
  onToggleFolder,
  onSelectFile,
}) => {
  const sorted = useMemo(() => sortNodes(nodes), [nodes]);

  return (
    <ul className={styles.treeList}>
      {sorted.map((node) => {
        if (isFolderNode(node)) {
          const open = expanded.has(node.path);
          return (
            <li key={node.path || node.name} className={styles.treeItem}>
              <button
                type="button"
                className={`${styles.treeRow} ${styles.treeRowFolder} ${
                  open ? styles.treeRowFolderOpen : ''
                }`}
                onClick={() => onToggleFolder(node.path)}
                aria-expanded={open}
              >
                <span className={styles.treeLeading}>
                  <CustomIcon
                    name={open ? 'caret-down' : 'caret-right'}
                    size={10}
                  />
                </span>
                <span className={styles.treeIcon}>
                  <CustomIcon name="inbox" size={13} />
                </span>
                <span className={styles.treeLabel}>{node.name}</span>
              </button>
              {open && node.children.length > 0 && (
                <div className={styles.treeGroup}>
                  <SkillFileTree
                    nodes={node.children}
                    selectedPath={selectedPath}
                    expanded={expanded}
                    onToggleFolder={onToggleFolder}
                    onSelectFile={onSelectFile}
                  />
                </div>
              )}
            </li>
          );
        }

        const active = selectedPath === node.path;
        const { stem, ext } = splitFileName(node.name);
        return (
          <li key={node.path} className={styles.treeItem}>
            <button
              type="button"
              className={`${styles.treeRow} ${styles.treeRowFile} ${
                active ? styles.treeRowActive : ''
              }`}
              onClick={() => onSelectFile(node.path)}
              aria-current={active ? 'true' : undefined}
            >
              <span className={styles.treeLeading} aria-hidden />
              <span className={styles.treeIcon}>
                <CustomIcon name="document" size={13} />
              </span>
              <span className={styles.treeLabel}>
                <span className={styles.treeStem}>{stem}</span>
                {ext && <span className={styles.treeExt}>{ext}</span>}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
};

export default SkillFileTree;

export const findFilePath = (
  nodes: SkillFileNode[],
  predicate: (node: SkillFileNode) => boolean
): string | null => {
  for (const node of nodes) {
    if (!isFolderNode(node) && predicate(node)) return node.path;
    if (isFolderNode(node) && node.children.length > 0) {
      const found = findFilePath(node.children, predicate);
      if (found) return found;
    }
  }
  return null;
};

export const folderPathsForFile = (filePath: string): string[] => {
  const parts = filePath.split('/');
  if (parts.length <= 1) return [];
  const folders: string[] = [];
  for (let i = 1; i < parts.length; i += 1) {
    folders.push(parts.slice(0, i).join('/'));
  }
  return folders;
};
