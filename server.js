'use strict';

const path = require('path');
const express = require('express');
const compression = require('compression');

const PORT = Number(process.env.PORT) || 8080;
const WWW_DIR = path.join(__dirname, 'frontend', 'www');
const INDEX_HTML = path.join(WWW_DIR, 'index.html');

/** Paths that look like static assets (have a file extension) must 404 if missing. */
const HAS_FILE_EXTENSION = /\.[a-zA-Z0-9]+$/;

const app = express();

app.use(compression());

app.use(
  express.static(WWW_DIR, {
    index: false,
    setHeaders(res, filePath) {
      const base = path.basename(filePath);
      if (base === 'index.html') {
        res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');
        return;
      }
      if (/\.(?:js|css)$/i.test(base)) {
        res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
      }
    },
  })
);

app.get('*', (req, res) => {
  const pathname = req.path || '/';
  if (HAS_FILE_EXTENSION.test(pathname)) {
    res.status(404).type('text/plain').send('Not Found');
    return;
  }
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');
  res.sendFile(INDEX_HTML);
});

app.listen(PORT, () => {
  console.log(`DeviceManager SPA server listening on port ${PORT}`);
  console.log(`Serving static files from ${WWW_DIR}`);
});
