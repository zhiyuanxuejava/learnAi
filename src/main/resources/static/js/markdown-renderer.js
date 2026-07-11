(function () {
  "use strict";

  function decodeSseContent(content) {
    try {
      return decodeURIComponent(content);
    }
    catch (error) {
      return content;
    }
  }

  function splitThinking(markdown) {
    const lower = markdown.toLowerCase();
    const start = lower.indexOf("<think>");

    if (start < 0) {
      return {
        thinking: "",
        answer: markdown
      };
    }

    const end = lower.indexOf("</think>", start + 7);
    if (end < 0) {
      return {
        thinking: markdown.slice(start + 7),
        answer: markdown.slice(0, start)
      };
    }

    return {
      thinking: markdown.slice(start + 7, end),
      answer: markdown.slice(0, start) + markdown.slice(end + 8)
    };
  }

  function renderMarkdown(markdown) {
    const normalizedMarkdown = normalizeMarkdown(markdown);
    const lines = normalizedMarkdown.replace(/\r\n/g, "\n").split("\n");
    const blocks = [];
    let index = 0;

    while (index < lines.length) {
      const line = lines[index];

      if (/^\s*[*+\-•·●▪◦]\s*$/.test(line)) {
        index += 1;
        continue;
      }

      if (!line.trim()) {
        index += 1;
        continue;
      }

      if (/^```/.test(line.trim())) {
        const codeLines = [];
        index += 1;
        while (index < lines.length && !/^```/.test(lines[index].trim())) {
          codeLines.push(lines[index]);
          index += 1;
        }
        if (index < lines.length) {
          index += 1;
        }
        blocks.push(`<pre><code>${escapeHtml(codeLines.join("\n"))}</code></pre>`);
        continue;
      }

      const heading = line.match(/^(#{1,6})\s*(.+)$/);
      if (heading) {
        const level = heading[1].length;
        blocks.push(`<h${level}>${renderInline(heading[2])}</h${level}>`);
        index += 1;
        continue;
      }

      if (/^\s*(?:---+|\*\*\*+|___+)\s*$/.test(line)) {
        blocks.push("<hr>");
        index += 1;
        continue;
      }

      if (/^\s*\$\$/.test(line)) {
        const mathLines = [];
        while (index < lines.length) {
          mathLines.push(lines[index]);
          const current = lines[index].trim();
          index += 1;
          if ((current.endsWith("$$") && mathLines.length > 1) || /^\s*\$\$.*\$\$\s*$/.test(current)) {
            break;
          }
        }
        blocks.push(`<div class="math-block">${escapeHtml(normalizeMath(mathLines.join("\n")))}</div>`);
        continue;
      }

      if (isTableRow(line)) {
        const tableRows = [];
        while (index < lines.length && isTableRow(lines[index])) {
          tableRows.push(splitTableRow(lines[index]));
          index += 1;
        }
        blocks.push(renderTable(tableRows));
        continue;
      }

      if (/^>\s?/.test(line)) {
        const quoteLines = [];
        while (index < lines.length && /^>\s?/.test(lines[index])) {
          quoteLines.push(lines[index].replace(/^>\s?/, ""));
          index += 1;
        }
        blocks.push(`<blockquote>${renderMarkdown(quoteLines.join("\n"))}</blockquote>`);
        continue;
      }

      if (isUnorderedListItem(line)) {
        const items = [];
        while (index < lines.length && isUnorderedListItem(lines[index])) {
          items.push(lines[index].replace(/^\s*[-*+•·●▪◦]\s+/, ""));
          index += 1;
        }
        const visibleItems = items.filter(item => item.trim() && item.trim() !== "-");
        if (visibleItems.length) {
          blocks.push(`<ul>${visibleItems.map(item => `<li>${renderInline(item)}</li>`).join("")}</ul>`);
        }
        continue;
      }

      if (/^\s*\d+[.)]\s*/.test(line)) {
        const items = [];
        while (index < lines.length && /^\s*\d+[.)]\s*/.test(lines[index])) {
          items.push(lines[index].replace(/^\s*\d+[.)]\s*/, ""));
          index += 1;
        }
        blocks.push(`<ol>${items.map(item => `<li>${renderInline(item)}</li>`).join("")}</ol>`);
        continue;
      }

      const paragraph = [];
      while (
        index < lines.length &&
        lines[index].trim() &&
        !/^(#{1,6})\s*/.test(lines[index]) &&
        !/^```/.test(lines[index].trim()) &&
        !/^\s*(?:---+|\*\*\*+|___+)\s*$/.test(lines[index]) &&
        !isTableRow(lines[index]) &&
        !/^>\s?/.test(lines[index]) &&
        !isUnorderedListItem(lines[index]) &&
        !/^\s*\d+[.)]\s*/.test(lines[index])
      ) {
        paragraph.push(lines[index]);
        index += 1;
      }
      blocks.push(`<p>${renderInline(paragraph.join(" "))}</p>`);
    }

    return blocks.join("");
  }

  function renderInline(text) {
    const mathTokens = [];
    const protectedText = normalizeInlineMarkdown(text)
      .replace(/\$\$([^$]+)\$\$/g, (_, formula) => stashMath(formula, mathTokens))
      .replace(/(^|[^$])\$([^$\n]+)\$(?!\$)/g, (_, prefix, formula) => prefix + stashMath(formula, mathTokens))
      .replace(/((?:良率|FPY|RTY|Y[_\w]*|[A-Za-z]+)\s*=\s*[^，。；;\n]*)/g, (_, formula) => stashMath(formula, mathTokens));

    return restoreMath(
      escapeHtml(protectedText)
        .replace(/`([^`]+)`/g, "<code>$1</code>")
        .replace(/\*\*([\s\S]+?)\*\*/g, "<strong>$1</strong>")
        .replace(/__([\s\S]+?)__/g, "<strong>$1</strong>")
        .replace(/\*([^*\s][^*:：]{0,30}[:：])\s*\*/g, "<strong>$1</strong> ")
        .replace(/\*([^*\n]+?)\*/g, "<em>$1</em>")
        .replace(/_([^_\n]+?)_/g, "<em>$1</em>")
        .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>'),
      mathTokens
    );
  }

  function stashMath(formula, mathTokens) {
    const token = `@@MATH_${mathTokens.length}@@`;
    mathTokens.push(`<span class="math-inline">${escapeHtml(normalizeMath(formula))}</span>`);
    return token;
  }

  function restoreMath(html, mathTokens) {
    return mathTokens.reduce(
      (result, mathHtml, index) => result.replaceAll(`@@MATH_${index}@@`, mathHtml),
      html
    );
  }

  function isTableRow(line) {
    const trimmed = line.trim();
    return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.split("|").length >= 4;
  }

  function isTableSeparator(cells) {
    return cells.every(cell => /^:?-{3,}:?$/.test(cell.trim()));
  }

  function splitTableRow(line) {
    return line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map(cell => cell.trim());
  }

  function renderTable(rows) {
    if (!rows.length) {
      return "";
    }

    const hasHeader = rows.length > 1 && isTableSeparator(rows[1]);
    const header = hasHeader ? rows[0] : [];
    const bodyRows = hasHeader ? rows.slice(2) : rows;

    const thead = hasHeader
      ? `<thead><tr>${header.map(cell => `<th>${renderInline(cell)}</th>`).join("")}</tr></thead>`
      : "";
    const tbody = `<tbody>${bodyRows.map(row => `<tr>${row.map(cell => `<td>${renderInline(cell)}</td>`).join("")}</tr>`).join("")}</tbody>`;

    return `<div class="table-scroll"><table>${thead}${tbody}</table></div>`;
  }

  function isUnorderedListItem(line) {
    return /^\s*[-*+•·●▪◦]\s+\S/.test(line);
  }

  function normalizeMarkdown(markdown) {
    return sanitizeMarkdown(markdown)
      .replace(/^(#{1,6})(\S)/gm, "$1 $2")
      .replace(/^(\s*[-*+•·●▪◦])(\S)/gm, "$1 $2")
      .split("\n")
      .map(rawLine => {
        const line = normalizeSectionTitleLine(rawLine);
        if (!/^\s*[-*+•·●▪◦]\s+\S/.test(line)) {
          return line;
        }
        return line.replace(/(\s)-(?=\S)/g, "$1\n- ");
      })
      .join("\n");
  }

  function sanitizeMarkdown(markdown) {
    return String(markdown)
      .replace(/\u00A0/g, " ")
      .replace(/<0xA0>/gi, "")
      .replace(/[\u200B-\u200D\uFEFF]/g, "")
      .replace(/\r/g, "");
  }

  function normalizeSectionTitleLine(line) {
    if (/^\s*(?:[-*+•·●▪◦])\s*\*{1,2}\s*$/.test(line)) {
      return "";
    }

    const matched = line.match(/^\s*(?:[-*+•·●▪◦])\s+(.{1,40}?)(?:[：:])?\s*\*{0,2}\s*$/);
    if (!matched) {
      return line;
    }

    const title = matched[1].trim();
    if (!isStandaloneSectionTitle(title)) {
      return line;
    }

    return `### ${title}`;
  }

  function isStandaloneSectionTitle(value) {
    const text = value.trim();
    if (!text || text.length > 40) {
      return false;
    }
    return !/[，。；;,.!?？！]$/.test(text);
  }

  function normalizeInlineMarkdown(text) {
    return text
      .replace(/\$\$([\s\S]*?)\$\$/g, (_, formula) => `$$${normalizeMath(formula)}$$`)
      .replace(/\$+\s*\\text\{([^}]+)}\s*\$+/g, "$1")
      .replace(/\\frac\{([^{}]+)}\{([^{}]+)}/g, "($1)/($2)")
      .replace(/\\leftrightarrow/g, "↔")
      .replace(/\\rightarrow/g, "→")
      .replace(/\\leftarrow/g, "←")
      .replace(/\\rightarrows?/g, "→")
      .replace(/\\leftarrows?/g, "←")
      .replace(/\\left\s*/g, "")
      .replace(/\\right\s*/g, "")
      .replace(/\\times/g, "×")
      .replace(/\\dots/g, "...")
      .replace(/\\_/g, "_")
      .replace(/\\%/g, "%")
      .replace(/\$+\s*→\s*\$+/g, "→")
      .replace(/\$+\s*←\s*\$+/g, "←")
      .replace(/\$+\s*↔\s*\$+/g, "↔")
      .replace(/\\\(/g, "")
      .replace(/\\\)/g, "")
      .replace(/\$(→|←|↔)\$/g, "$1");
  }

  function normalizeMath(text) {
    return text
      .replace(/^\s*\$\$|\$\$\s*$/g, "")
      .replace(/^\s*\$|\$\s*$/g, "")
      .replace(/\\text\{([^}]+)}/g, "$1")
      .replace(/\\frac\{([^{}]+)}\{([^{}]+)}/g, "($1)/($2)")
      .replace(/\\leftrightarrow/g, "↔")
      .replace(/\\rightarrow/g, "→")
      .replace(/\\leftarrow/g, "←")
      .replace(/\\left\s*/g, "")
      .replace(/\\right\s*/g, "")
      .replace(/\\times/g, "×")
      .replace(/\\div/g, "÷")
      .replace(/\\dots/g, "...")
      .replace(/\\_/g, "_")
      .replace(/\\%/g, "%")
      .replace(/\s+/g, " ")
      .trim();
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  window.MarkdownRenderSupport = {
    decodeSseContent,
    splitThinking,
    renderMarkdown,
    escapeHtml
  };
})();
