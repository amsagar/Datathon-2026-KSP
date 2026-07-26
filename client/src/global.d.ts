declare module '*.module.scss' {
  const classes: { readonly [key: string]: string };
  export = classes;
}

declare module '*.module.css' {
  const classes: { readonly [key: string]: string };
  export = classes;
}

declare module '*.scss';
declare module '*.css';

declare module '*.svg' {
  import * as React from 'react';
  export const ReactComponent: React.FunctionComponent<
    React.SVGProps<SVGSVGElement>
  >;
  const src: string;
  export default src;
}

declare module '*.png';
declare module '*.jpg';
declare module '*.jpeg';
declare module '*.gif';
declare module '*.webp';
