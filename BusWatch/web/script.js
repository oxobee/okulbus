// 1. Intersection Observer for Staggered Scroll Reveals
const initScrollReveal = () => {
    const observerOptions = {
        threshold: 0.1,
        rootMargin: "0px 0px -50px 0px"
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('reveal-visible');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    const elements = document.querySelectorAll('.feature-card, .ui-item, .team-card, .section-title, .hero h1, .hero p, .btn');
    elements.forEach((el, index) => {
        el.classList.add('reveal-hidden');
        observer.observe(el);
    });
};

// 2. Typing Effect for Hero Title
const initTypingEffect = () => {
    const title = document.querySelector('.hero h1');
    if (!title) return;

    const text = title.innerText;
    title.innerText = '';
    title.style.visibility = 'visible';

    let i = 0;
    const type = () => {
        if (i < text.length) {
            title.innerHTML += text.charAt(i) === ',' ? ',<br>' : text.charAt(i);
            i++;
            setTimeout(type, 70);
        }
    };
    type();
};

// 3. Magnetic / Hover Parallax Effect for Screenshot Cards
const initTiltEffect = () => {
    const cards = document.querySelectorAll('.ui-item');

    cards.forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;

            const centerX = rect.width / 2;
            const centerY = rect.height / 2;

            const rotateX = (y - centerY) / 10;
            const rotateY = (centerX - x) / 10;

            card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-10px)`;
        });

        card.addEventListener('mouseleave', () => {
            card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) translateY(0)';
        });
    });
};

// 4. Hero Bus Parallax (Subtle mouse follow)
const initBusParallax = () => {
    const bus = document.querySelector('.hero-bus');
    if (!bus) return;

    window.addEventListener('mousemove', (e) => {
        const moveX = (e.clientX - window.innerWidth / 2) * 0.02;
        const moveY = (e.clientY - window.innerHeight / 2) * 0.02;
        bus.style.transform = `translate(${moveX}px, ${moveY}px)`;
    });
};

// 5. Scroll Progress Bar
const initProgressBar = () => {
    const progress = document.createElement('div');
    progress.className = 'scroll-progress';
    document.body.appendChild(progress);

    window.addEventListener('scroll', () => {
        const totalHeight = document.body.scrollHeight - window.innerHeight;
        const progressWidth = (window.pageYOffset / totalHeight) * 100;
        progress.style.width = progressWidth + "%";
    });
};

// 6. Back to Top Button
const initBackToTop = () => {
    const btn = document.createElement('button');
    btn.innerHTML = '<i class="fas fa-arrow-up"></i>';
    btn.className = 'back-to-top';
    document.body.appendChild(btn);

    window.addEventListener('scroll', () => {
        if (window.pageYOffset > 300) {
            btn.classList.add('visible');
        } else {
            btn.classList.remove('visible');
        }
    });

    btn.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });
};

// 7. Image Fallbacks
const initImageFallbacks = () => {
    document.querySelectorAll('.team-img').forEach(img => {
        img.addEventListener('error', function() {
            const name = this.alt.replace(/\s+/g, '+');
            this.src = `https://ui-avatars.com/api/?name=${name}&background=FEBE1E&color=2D2D2D&size=200`;
        }, { once: true });
    });

    document.querySelectorAll('.screenshot').forEach(img => {
        img.addEventListener('error', function() {
            const label = this.alt || 'App Preview';
            const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="480" viewBox="0 0 300 480">
                <rect width="300" height="480" rx="35" fill="#2D2D2D"/>
                <circle cx="150" cy="180" r="45" fill="#FEBE1E"/>
                <text x="150" y="195" fill="#2D2D2D" font-family="sans-serif" font-size="40" text-anchor="middle">🚌</text>
                <text x="150" y="270" fill="#FEBE1E" font-family="Poppins, sans-serif" font-size="16" font-weight="600" text-anchor="middle">${label}</text>
                <text x="150" y="295" fill="#888888" font-family="Poppins, sans-serif" font-size="12" text-anchor="middle">Okulbus App</text>
            </svg>`;
            this.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
        }, { once: true });
    });
};

// Initialize All
window.addEventListener('DOMContentLoaded', () => {
    initScrollReveal();
    initBusParallax();
    initProgressBar();
    initBackToTop();
    initImageFallbacks();
    initTiltEffect();

    // Slight delay for typing effect to feel more organic
    setTimeout(initTypingEffect, 500);
});
