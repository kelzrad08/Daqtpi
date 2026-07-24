const grid = document.getElementById("videoGrid");

videos.forEach(video => {

    const card = document.createElement("div");
    card.className = "video-card";

    card.innerHTML = `
        <div class="video-wrapper" data-id="${video.id}">
            <img
                loading="lazy"
                src="https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
                alt="${video.title}">
        </div>

        <div class="video-caption">
            <div class="video-dot"></div>
            <span>${video.title}</span>
        </div>
    `;

    grid.appendChild(card);

});

document.addEventListener("click", function(e){

    const wrapper = e.target.closest(".video-wrapper");

    if(!wrapper) return;

    if(wrapper.querySelector("iframe")) return;

    const id = wrapper.dataset.id;

    wrapper.innerHTML = `
        <iframe
            src="https://www.youtube.com/embed/${id}?autoplay=1"
            allowfullscreen
            frameborder="0"
            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture">
        </iframe>
    `;

});