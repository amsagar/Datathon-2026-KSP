import type React from 'react';

/**
 * Contract for a `kind: 'component'` UI template. The assistant emits `{ template, data }`; the
 * client looks up the registered component by key and renders it natively (no iframe), passing the
 * emitted `data` straight through as props. Components should be defensive — `data` is whatever the
 * model produced and was only loosely validated against the template's JSON Schema.
 */
export interface TemplateComponentProps {
  data: unknown;
}

export type TemplateComponent = React.FC<TemplateComponentProps>;
