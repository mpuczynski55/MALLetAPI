package com.agh.mallet.domain.user.user.control.service;

import com.agh.api.UserDTO;
import com.agh.api.UserDetailDTO;
import com.agh.api.UserLogInDTO;
import com.agh.api.UserRegistrationDTO;
import com.agh.mallet.domain.group.control.ContributionRepository;
import com.agh.mallet.domain.user.user.control.exception.MalletUserException;
import com.agh.mallet.domain.user.user.control.repository.UserRepository;
import com.agh.mallet.domain.user.user.control.utils.EmailTemplateProvider;
import com.agh.mallet.domain.user.user.control.utils.UserValidator;
import com.agh.mallet.domain.user.user.entity.ConfirmationTokenJPAEntity;
import com.agh.mallet.domain.user.user.entity.UserJPAEntity;
import com.agh.mallet.infrastructure.exception.ExceptionType;
import com.agh.mallet.infrastructure.mapper.UserDTOMapper;
import com.agh.mallet.infrastructure.mapper.UserInformationDTOMapper;
import com.agh.mallet.infrastructure.utils.ObjectIdentifierProvider;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class UserService  {

    private static final String USER_ALREADY_EXISTS_ERROR_MSG = "User with provided email: {0} already exists";
    private static final String USER_NOT_FOUND_ERROR_MSG = "User with provided email: {0} does not exist";

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final ConfirmationTokenService confirmationTokenService;
    private final EmailService emailService;
    private final Pbkdf2PasswordEncoder pbkdf2PasswordEncoder;
    private final ObjectIdentifierProvider objectIdentifierProvider;
    private final ContributionRepository contributionRepository;

    public UserService(UserRepository userRepository, UserValidator userValidator, ConfirmationTokenService confirmationTokenService, EmailService emailService, Pbkdf2PasswordEncoder pbkdf2PasswordEncoder, ObjectIdentifierProvider objectIdentifierProvider, ContributionRepository contributionRepository) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.confirmationTokenService = confirmationTokenService;
        this.emailService = emailService;
        this.pbkdf2PasswordEncoder = pbkdf2PasswordEncoder;
        this.objectIdentifierProvider = objectIdentifierProvider;
        this.contributionRepository = contributionRepository;
    }

    @SneakyThrows
    public void signUp(UserRegistrationDTO userInfo) {
        String email = userInfo.email();
        userValidator.validateEmail(email);

        Optional<UserJPAEntity> userJPAEntity = userRepository.findByEmailIgnoreCase(email);
        userJPAEntity.ifPresent(throwUserAlreadyExistsException());

        UserJPAEntity user = mapToUserEntity(userInfo);
        save(user);

        ConfirmationTokenJPAEntity confirmationToken = confirmationTokenService.save(user);

        String confirmationURL = "https://mallet.onrender.com" + "/user/registration/confirm?token=" + confirmationToken.getToken();

        emailService.sendMail("Mallet account confirmation", email, EmailTemplateProvider.getEmailConfirmationTemplate(confirmationURL));
    }

    @Lock(LockModeType.WRITE)
    private UserJPAEntity mapToUserEntity(UserRegistrationDTO userInfo) {
        String encodedPassword = pbkdf2PasswordEncoder.encode(userInfo.password());
        String username = userInfo.username();
        LocalDate registrationDate = LocalDate.now();
        String email = userInfo.email();
        String identifier = objectIdentifierProvider.fromUsername(username);

        return new UserJPAEntity(username, encodedPassword, registrationDate, email, identifier);
    }

    private Consumer<UserJPAEntity> throwUserAlreadyExistsException() {
        return user -> {
            String errorMsg = MessageFormat.format(USER_ALREADY_EXISTS_ERROR_MSG, user.getEmail());
            throw new MalletUserException(errorMsg, ExceptionType.ALREADY_EXISTS);
        };
    }

    public UserDetailDTO logIn(UserLogInDTO userInfo) {
        String email = userInfo.email();
        userValidator.validateEmail(email);

        UserJPAEntity userEntity = getByEmail(email);

        userValidator.validateUserLogIn(userEntity, userInfo.password());

        return UserInformationDTOMapper.from(userEntity);
    }

    private Supplier<RuntimeException> throwUserNotFoundException(String email) {
        return () -> {
            String errorMsg = MessageFormat.format(USER_NOT_FOUND_ERROR_MSG, email);
            throw new MalletUserException(errorMsg, ExceptionType.NOT_FOUND);
        };
    }

    public UserJPAEntity getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(throwUserNotFoundException(email));
    }

    public UserJPAEntity save(UserJPAEntity userEntity) {
        return userRepository.save(userEntity);
    }

    public List<UserDTO> get(String username) {
        if (username.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserJPAEntity> users = userRepository.findAllByUsernameContainingIgnoreCaseAndEnabled(username, true);

        return UserDTOMapper.from(users);
    }

    @Transactional
    public void delete(long id, String email){
        UserJPAEntity user = userRepository.findById(id)
                .orElseThrow(throwUserNotFoundException(String.valueOf(id)));

        if(!user.getEmail().equals(email)){
            throw new MalletUserException("Cannot perform this operation", ExceptionType.FORBIDDEN);
        }

       long countOfAdminGroups = user.getUserGroups().stream()
                .filter(s -> s.getAdmin().equals(user))
                .count();

        if(countOfAdminGroups > 0){
            throw new MalletUserException("Cannot perform this operation.", ExceptionType.FORBIDDEN);
        }

        contributionRepository.deleteAllByContributor(user);
        userRepository.delete(user);
    }
}
