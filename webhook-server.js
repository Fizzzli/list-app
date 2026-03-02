const http = require('http');
const fs = require('fs');

const PORT = process.env.WEBHOOK_PORT || 8888;
const LOG_FILE = '/home/node/.openclaw/workspace/list-app/webhook-events.log';

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/github') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      const event = req.headers['x-github-event'];
      const delivery = req.headers['x-github-delivery'];
      const timestamp = new Date().toISOString();
      
      const logEntry = JSON.stringify({
        timestamp,
        event,
        delivery,
        payload: JSON.parse(body || '{}')
      }, null, 2);
      
      fs.appendFileSync(LOG_FILE, logEntry + '\n\n');
      
      console.log(`[${timestamp}] ${event} - ${delivery}`);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ received: true, event, delivery }));
    });
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Webhook server listening on port ${PORT}`);
  console.log(`Endpoint: http://0.0.0.0:${PORT}/github`);
});
