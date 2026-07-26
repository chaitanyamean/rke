/**
 * Opens a print-ready HTML page in a new window and triggers window.print().
 * Used by all report pages for "Download PDF" (save-as-PDF via browser print dialog).
 *
 * @param title     Heading shown inside the printed page (h1)
 * @param subtitle  Sub-line shown below the heading (supports safe HTML)
 * @param tableHtml The table HTML to embed
 * @param filename  Optional: overrides the browser's suggested save filename (window title).
 *                  If omitted, falls back to title.
 */
export function printReport(title: string, subtitle: string, tableHtml: string, filename?: string) {
  const win = window.open('', '_blank', 'width=1200,height=850')
  if (!win) return

  win.document.write(`<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <title>${esc(filename ?? title)}</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: Arial, sans-serif; font-size: 11px; color: #1e293b; padding: 24px; }
    h1  { font-size: 17px; font-weight: 700; margin-bottom: 3px; }
    .meta { font-size: 10px; color: #64748b; margin-bottom: 14px; }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f8fafc; border: 1px solid #cbd5e1; padding: 5px 7px;
         font-size: 9px; text-transform: uppercase; letter-spacing: 0.04em;
         color: #475569; text-align: left; white-space: nowrap; }
    td { border: 1px solid #e2e8f0; padding: 4px 7px; vertical-align: top; font-size: 10px; }
    tfoot td { background: #f1f5f9; font-weight: 700; border-top: 2px solid #94a3b8; }
    .right  { text-align: right; }
    .debit  { color: #dc2626; }
    .credit { color: #16a34a; }
    .bal-neg { color: #b91c1c; font-weight: 700; }
    .bal-pos { color: #15803d; font-weight: 700; }
    .muted  { color: #94a3b8; }
    .badge  { display: inline-block; padding: 1px 5px; border-radius: 3px; font-size: 9px; font-weight: 600; }
    .badge-d { background: #fef2f2; color: #dc2626; }
    .badge-c { background: #f0fdf4; color: #16a34a; }
    @media print { @page { size: A4 landscape; margin: 12mm; } body { padding: 0; } }
  </style>
</head>
<body>
  <h1>${esc(title)}</h1>
  <p class="meta">${subtitle}</p>
  ${tableHtml}
  <script>window.onload = function(){ window.print(); }<\/script>
</body>
</html>`)
  win.document.close()
}

export function esc(s: string | null | undefined): string {
  return (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
