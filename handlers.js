function onButton6Click() {
  var apiKey = "98c4c2808160b694ff2e08b844fe2032";
  var players = [1, 2, 3, 4, 5, 276, 306, 629, 874, 184];
  var randomId = players[Math.floor(Math.random() * players.length)];

  fetch("https://v3.football.api-sports.io/players?id=" + randomId + "&season=2024", {
    method: "GET",
    headers: {
      "x-apisports-key": apiKey
    }
  })
    .then(function (response) { return response.json(); })
    .then(function (data) {
      var p = data.response[0].player;
      var s = data.response[0].statistics[0];
      var newTab = window.open();
      newTab.document.write(`
        <html>
          <head><title>${p.firstname} ${p.lastname}</title></head>
          <body style="font-family: Arial, sans-serif; max-width: 650px; margin: 60px auto; padding: 20px; background: #f4f4f4;">
            <div style="background: white; border-radius: 12px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
              <img src="${p.photo}" style="width:100px; border-radius:50%; float:right;">
              <h1 style="color:#37003c;">${p.firstname} ${p.lastname}</h1>
              <p><strong>🎂 Age:</strong> ${p.age}</p>
              <p><strong>🌍 Nationality:</strong> ${p.nationality}</p>
              <p><strong>📏 Height:</strong> ${p.height}</p>
              <p><strong>⚖️ Weight:</strong> ${p.weight}</p>
              <hr>
              <h2 style="color:#37003c;">2024/25 Season Stats</h2>
              <p><strong>🏟️ Team:</strong> ${s.team.name}</p>
              <p><strong>🎮 Appearances:</strong> ${s.games.appearences}</p>
              <p><strong>⚽ Goals:</strong> ${s.goals.total}</p>
              <p><strong>🎯 Assists:</strong> ${s.goals.assists}</p>
              <p><strong>🟨 Yellow Cards:</strong> ${s.cards.yellow}</p>
              <p><strong>🟥 Red Cards:</strong> ${s.cards.red}</p>
              <p><strong>⏱️ Minutes Played:</strong> ${s.games.minutes}</p>
              <hr>
              <small>Powered by <a href="https://www.api-football.com">API-Football</a></small>
            </div>
          </body>
        </html>
      `);
    })
    .catch(function (error) {
      alert("Failed to fetch player data. Please try again.");
    });
}