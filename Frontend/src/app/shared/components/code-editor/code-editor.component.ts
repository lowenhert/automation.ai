import {
  Component, ElementRef, ViewEncapsulation, computed, input, model, viewChild
} from '@angular/core';
import { HighlightLanguage, highlight } from './syntax-highlight';

/**
 * Code editor: a transparent textarea over an HTML-escaped, syntax-highlighted copy of the text, with line numbers.
 * Tab inserts four spaces; Shift+Tab removes indentation.
 */
@Component({
  selector: 'app-code-editor',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="aai-editor" [style.height]="height()">
      <div class="aai-editor-gutter" #gutter aria-hidden="true">
        @for (n of lineNumbers(); track n) {
          <div [class.aai-editor-error]="errorLines().includes(n)">{{ n }}</div>
        }
      </div>
      <div class="aai-editor-body">
        <pre class="aai-editor-highlight" #highlightEl aria-hidden="true"><code [innerHTML]="highlighted()"></code></pre>
        <textarea #textarea class="aai-editor-input" spellcheck="false" autocapitalize="off" autocomplete="off"
          [attr.aria-label]="ariaLabel()" [readOnly]="readonly()" [value]="value()"
          (input)="onInput($event)" (keydown)="onKeydown($event)" (scroll)="syncScroll()"></textarea>
      </div>
    </div>
  `,
  styles: [`
    .aai-editor { display: flex; border-radius: 0.75rem; overflow: hidden; border: 1px solid #e4e7ec; background: #0f172a;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 13px; line-height: 20px; }
    .dark .aai-editor { border-color: #344054; }
    .aai-editor-gutter { flex: none; min-width: 3rem; padding: 12px 8px; text-align: right; color: #64748b;
      background: #0b1222; overflow: hidden; user-select: none; }
    .aai-editor-gutter .aai-editor-error { color: #f97066; font-weight: 600; }
    .aai-editor-body { position: relative; flex: 1; min-width: 0; }
    .aai-editor-highlight, .aai-editor-input { position: absolute; inset: 0; margin: 0; padding: 12px; border: 0;
      font: inherit; line-height: inherit; tab-size: 4; white-space: pre; overflow: auto; box-sizing: border-box; }
    .aai-editor-highlight { color: #e2e8f0; pointer-events: none; }
    .aai-editor-highlight code { font: inherit; }
    .aai-editor-input { color: transparent; background: transparent; caret-color: #f8fafc; resize: none; outline: none; }
    .aai-editor-input::selection { background: rgba(70, 95, 255, 0.35); color: transparent; }
    .aai-editor-input[readonly] { caret-color: transparent; }
    .tok-comment { color: #64748b; font-style: italic; }
    .tok-string { color: #a5d6a7; }
    .tok-keyword { color: #c792ea; }
    .tok-builtin { color: #82aaff; }
    .tok-function { color: #ffcb6b; }
    .tok-number { color: #f78c6c; }
    .tok-decorator { color: #89ddff; }
    .tok-variable { color: #f07178; }
    .tok-section { color: #89ddff; font-weight: 600; }
  `]
})
export class CodeEditorComponent {
  value = model<string>('');
  language = input<HighlightLanguage>('PYTEST');
  readonly = input<boolean>(false);
  height = input<string>('28rem');
  errorLines = input<number[]>([]);
  ariaLabel = input<string>('Script code');

  private textarea = viewChild.required<ElementRef<HTMLTextAreaElement>>('textarea');
  private highlightEl = viewChild.required<ElementRef<HTMLPreElement>>('highlightEl');
  private gutter = viewChild.required<ElementRef<HTMLDivElement>>('gutter');

  // A trailing newline keeps the highlighted copy as tall as the textarea content
  highlighted = computed(() => highlight(this.value() ?? '', this.language()) + '\n');
  lineNumbers = computed(() => Array.from({ length: (this.value() ?? '').split('\n').length }, (_, i) => i + 1));

  onInput(event: Event): void {
    this.value.set((event.target as HTMLTextAreaElement).value);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab' || this.readonly()) return;
    event.preventDefault();
    const ta = this.textarea().nativeElement;
    const { selectionStart: start, selectionEnd: end, value } = ta;
    if (event.shiftKey) {
      const lineStart = value.lastIndexOf('\n', start - 1) + 1;
      const remove = /^ {1,4}/.exec(value.slice(lineStart))?.[0].length ?? 0;
      if (!remove) return;
      this.apply(value.slice(0, lineStart) + value.slice(lineStart + remove), Math.max(lineStart, start - remove), Math.max(lineStart, end - remove));
    } else {
      this.apply(value.slice(0, start) + '    ' + value.slice(end), start + 4, start + 4);
    }
  }

  syncScroll(): void {
    const ta = this.textarea().nativeElement;
    this.highlightEl().nativeElement.scrollTop = ta.scrollTop;
    this.highlightEl().nativeElement.scrollLeft = ta.scrollLeft;
    this.gutter().nativeElement.scrollTop = ta.scrollTop;
  }

  private apply(text: string, selStart: number, selEnd: number): void {
    const ta = this.textarea().nativeElement;
    ta.value = text;
    ta.setSelectionRange(selStart, selEnd);
    this.value.set(text);
  }
}
