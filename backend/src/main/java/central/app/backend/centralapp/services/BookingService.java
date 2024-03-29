package central.app.backend.centralapp.services;

import central.app.backend.centralapp.exceptions.BookingNotExistException;
import central.app.backend.centralapp.exceptions.UnauthorizedAccessException;
import central.app.backend.centralapp.forms.BookingForm;
import central.app.backend.centralapp.forms.PageForm;
import central.app.backend.centralapp.models.Booking;
import central.app.backend.centralapp.models.User;
import central.app.backend.centralapp.repositories.BookingRepository;
import central.app.backend.centralapp.repositories.UserRepository;
import central.app.backend.centralapp.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class BookingService {

    private BookingRepository bookingRepository;
    private UserService userService;

    @Autowired
    public BookingService(BookingRepository bookingRepository, UserService userService) {
        this.bookingRepository = bookingRepository;
        this.userService = userService;
    }

    public Booking create(Booking booking) {
        return bookingRepository.save(booking);
    }

    private static <T> List<List<T>> getPages(Collection<T> c, Integer pageSize) {
        if (c == null)
            return Collections.emptyList();
        List<T> list = new ArrayList<>(c);
        if (pageSize == null || pageSize <= 0 || pageSize > list.size())
            pageSize = list.size();
        int numPages = (int) Math.ceil((double)list.size() / (double)pageSize);
        List<List<T>> pages = new ArrayList<>(numPages);
        for (int pageNum = 0; pageNum < numPages;)
            pages.add(list.subList(pageNum * pageSize, Math.min(++pageNum * pageSize, list.size())));
        return pages;
    }

    public PageForm getAll(String startDateFrom, String startDateTo, String endDateFrom, String endDateTo, Double costUp, Double costDown, String post_code, String city, String street,  String username, Integer pageSize, Integer pageNumber, User currentUser) {
        List<Booking> bookings;
        if (currentUser.getRole().equals("USER"))
            bookings = bookingRepository.findByOwner(currentUser.getId());
        else
            bookings = bookingRepository.findAll();

        int maxSize = 0;
        boolean isNext = false;
        List<BookingForm> bookingForms = new ArrayList<>();
        for (Booking book : bookings) {
            bookingForms.add(new BookingForm(book, userService.getUsername(book.getOwner())));
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        if (startDateFrom != null && !startDateFrom.equals("")) {
            LocalDate dateFrom = LocalDate.parse(startDateFrom, df);
            bookingForms.removeIf(booking -> dateFrom.compareTo(booking.getStartDateTime()) > 0);
        }
        if (startDateTo != null && !startDateTo.equals("")) {
            LocalDate dateTo = LocalDate.parse(startDateTo, df);
            bookingForms.removeIf(booking -> dateTo.compareTo(booking.getStartDateTime()) < 0);
        }
        if (endDateFrom != null && !endDateFrom.equals("")) {
            LocalDate dateFrom = LocalDate.parse(endDateFrom, df);
            bookingForms.removeIf(booking -> dateFrom.compareTo(booking.getEndDateTime()) > 0);
        }
        if (endDateTo != null && !endDateTo.equals("")) {
            LocalDate dateTo = LocalDate.parse(endDateTo, df);
            bookingForms.removeIf(booking -> dateTo.compareTo(booking.getEndDateTime()) < 0);
        }
        if(costUp != null && costUp > 0){
            bookingForms.removeIf(booking -> costUp < booking.getCostPerDay());
        }
        if(costDown != null && costDown > 0) {
            bookingForms.removeIf(booking -> costDown > booking.getCostPerDay());
        }
        if (post_code != null && !post_code.equals("")) {
            bookingForms.removeIf(booking -> post_code.compareTo(booking.getPostCode()) != 0);
        }
        if (city != null && !city.equals("")) {
            bookingForms.removeIf(booking -> city.compareTo(booking.getCity()) != 0);
        }
        if (street != null && !street.equals("")) {
            bookingForms.removeIf(booking -> street.compareTo(booking.getStreet()) != 0);
        }
        if (username != null && !username.equals(""))
            bookingForms.removeIf(booking -> !username.equals(booking.getUsername()));
        if(pageSize != null && pageNumber != null && pageSize != 0 && pageNumber != 0){
            pageNumber -= 1;
            if(pageSize > 0 && pageSize <= bookingForms.size()){
                maxSize = (int)Math.floor(bookingForms.size()/pageSize);
                if((bookingForms.size()%pageSize)!=0)
                    maxSize+=1;
            }
            if(pageSize > 0 && pageNumber >= 0 && pageSize <= bookingForms.size() && (pageNumber+1)<=maxSize){
                List<List<BookingForm>> page = getPages(bookingForms, pageSize);
                if(page.size()>pageNumber)
                    bookingForms = page.get(pageNumber);
                isNext = maxSize != (pageNumber + 1);
            }else {
                bookingForms.clear();
            }
        }
        PageForm pageForm = new PageForm(bookingForms,maxSize,isNext);
        return pageForm;

    }

    public BookingForm get(int id, User currentUser) {
        Booking booking = bookingRepository.findById(id);
        if (booking == null)
            throw new BookingNotExistException("Id " + id);
        if(currentUser.getRole().equals("USER") && currentUser.getId() != booking.getOwner())
            throw new AccessDeniedException("This user does not have access to this booking: " + id);
        return new BookingForm(booking,userService.getUsername(booking.getOwner()));
    }

    public String delete(int id, User currentUser) {
        Booking booking = bookingRepository.findById(id);
        if (booking == null)
            throw new BookingNotExistException("Id: " + id);
        if (currentUser.getRole().equals("USER") && currentUser.getId() != booking.getOwner())
            throw new AccessDeniedException("This user does not have access to this booking: " + id);
        bookingRepository.delete(booking);
        return "Booking Deleted";
    }

    public BookingForm update(int id, Booking userToUpdate, User currentUser) {
        Booking user = bookingRepository.findById(id);
        if (user == null)
            throw new BookingNotExistException("Id: " + id);
        if (currentUser.getRole().equals("USER") && currentUser.getId() != user.getOwner())
            throw new AccessDeniedException("This user does not have access to this booking: " + id);
        user.setAll(userToUpdate);
        user = bookingRepository.save(user);
        return new BookingForm(user,userService.getUsername(user.getOwner()));
    }
}