/**
 * Exports the rendered chat thread to a local PDF (PS requirement: "Save the Conversation History
 * in PDF format locally").
 *
 * Uses the browser's own print-to-PDF pipeline rather than rasterising with html2canvas. That keeps
 * the output **searchable/selectable text** (not a flat image), renders SVG charts as vectors, and —
 * critically — preserves Kannada glyphs via the system font (jsPDF's built-in fonts cannot render
 * Kannada, which is why the old image-based export existed). The conversation is cloned into an
 * isolated print window with the app's stylesheets copied over, so only the transcript prints —
 * not the surrounding app chrome.
 */
export async function exportChatToPdf(elementId: string, title: string): Promise<void> {
  const element = document.getElementById(elementId);
  if (!element) throw new Error('Chat thread not found');

  // The thread scrolls inside a descendant; grab the tallest scroll container so the whole
  // conversation is captured, not just the visible viewport.
  let target: HTMLElement = element;
  element.querySelectorAll<HTMLElement>('*').forEach((el) => {
    if (el.scrollHeight > target.scrollHeight) target = el;
  });

  const win = window.open('', '_blank', 'width=900,height=1200');
  if (!win) {
    throw new Error('Popup blocked — allow popups for this site to export the PDF.');
  }

  const doc = win.document;
  const safeTitle = title.replace(/[^\wಀ-೿ -]/g, '').slice(0, 60) || 'conversation';
  doc.title = safeTitle;

  // Copy the app's stylesheets so the printed transcript keeps its styling.
  document
    .querySelectorAll('link[rel="stylesheet"], style')
    .forEach((node) => doc.head.appendChild(node.cloneNode(true)));

  const printStyle = doc.createElement('style');
  printStyle.textContent = `
    @page { size: A4; margin: 12mm; }
    html, body { background: #fff; margin: 0; padding: 0; }
    * { -webkit-print-color-adjust: exact; print-color-adjust: exact;
        max-height: none !important; overflow: visible !important; }
    .export-root { height: auto !important; }
  `;
  doc.head.appendChild(printStyle);

  const clone = target.cloneNode(true) as HTMLElement;
  clone.classList.add('export-root');
  clone.style.height = 'auto';
  clone.style.maxHeight = 'none';
  clone.style.overflow = 'visible';
  doc.body.appendChild(clone);

  // Let cloned stylesheets/fonts/images settle before invoking the print dialog.
  win.focus();
  await new Promise((resolve) => win.setTimeout(resolve, 500));
  win.print();
}
