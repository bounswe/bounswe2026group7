function onButton1Click() {

  let img = document.getElementById("manifestImage");

  if (!img) {
    img = document.createElement("img");
    img.id = "manifestImage";
    img.style.marginTop = "20px";
    img.style.maxWidth = "400px";
    document.body.appendChild(img);
  }

  // Her basışta yeni foto
  img.src = "https://picsum.photos/400?random=" + new Date().getTime();

}

function onButton2Click() {
  alert("2. buton çalıştı");
}

function onButton3Click() {
  alert("3. buton çalıştı");
}